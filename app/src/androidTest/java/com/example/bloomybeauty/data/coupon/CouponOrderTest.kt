package com.example.bloomybeauty.data.coupon

import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class CouponOrderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var orders: OrderRepository
    private lateinit var cart: CartRepository
    private var owner = 0L
    private val clock = AtomicLong()
    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB);helper = AuthDatabaseHelper(context,DB)
        owner = (AuthRepository(helper).register("Khách mã giảm","coupon@bloomy.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        cart = CartRepository(helper);cart.add(owner,"cleanser");cart.setQuantity(owner,"cleanser",3)
        cart.saveShipping(owner,ShippingDetails("Người nhận","0912345678","123 Nguyễn Huệ, Thành phố Hồ Chí Minh"))
        clock.set(System.currentTimeMillis())
        helper.writableDatabase.execSQL("DELETE FROM coupon_rules")
        val start = clock.get()-1000;val end = clock.get()+86400000
        helper.writableDatabase.execSQL("INSERT INTO coupon_rules VALUES('PCT20','PERCENT',20,200000,50000,?,?,1,0)",arrayOf(start,end))
        helper.writableDatabase.execSQL("INSERT INTO coupon_rules VALUES('FIX50','FIXED',50000,500000,NULL,?,?,1,0)",arrayOf(start,end))
        orders = OrderRepository(helper,coupons = LocalCouponRepository(true) {clock.get()})
    }
    @After fun cleanup() {helper.close();context.deleteDatabase(DB)}
    private fun stock() = helper.readableDatabase.rawQuery("SELECT stock FROM products WHERE id='cleanser'",null).use {it.moveToFirst();it.getInt(0)}
    private suspend fun couponError(error: CouponError, block: suspend () -> Unit) {
        try {block();fail("Expected $error")} catch(e: CouponException) {assertEquals(error,e.error)}
    }
    private suspend fun orderError(error: OrderError, block: suspend () -> Unit) {
        try {block();fail("Expected $error")} catch(e: OrderException) {assertEquals(error,e.error)}
    }
    @Test fun appliedDiscountAndRuleSnapshotSurviveRuleDeletionAndReplay() = runBlocking {
        val request = orders.prepare(owner," pct20 ")
        assertEquals("PCT20",request.coupon!!.rule.code)
        assertEquals(576_000L,request.subtotalVnd);assertEquals(50_000L,request.discountVnd)
        assertEquals(0L,request.feeVnd);assertEquals(526_000L,request.totalVnd)
        val order = orders.create(owner,request)
        assertEquals("PCT20",order.couponCode);assertEquals(50_000L,order.discountVnd)
        assertEquals("PERCENT",org.json.JSONObject(order.couponRuleSnapshot!!).getString("kind"))
        helper.writableDatabase.execSQL("DELETE FROM coupon_rules WHERE code='PCT20'")
        cart.add(owner,"serum")
        assertEquals(order,orders.create(owner,request));assertEquals(27,stock())
        assertEquals("serum",cart.load(owner).items.single().product.id)
        assertEquals(order,orders.detail(owner,order.id))
    }
    @Test fun expiryAfterReviewRequiresExplicitNewQuoteAndNeverDropsCouponSilently() = runBlocking {
        val request = orders.prepare(owner,"PCT20")
        clock.set(request.coupon!!.rule.endsAt)
        couponError(CouponError.EXPIRED) {orders.create(owner,request)}
        assertTrue(orders.list(owner).isEmpty());assertEquals(30,stock());assertEquals(3,cart.load(owner).quantity)
        assertEquals(request,orders.recover(owner)!!.request)
        val removed = orders.prepare(owner,"")
        assertNotEquals(request.id,removed.id);assertNull(removed.coupon);assertEquals(576_000L,removed.totalVnd)
        assertEquals(0L,orders.create(owner,removed).discountVnd)
    }
    @Test fun changedRevisionOrConditionsRequireReconfirmationEvenWithoutRevisionBump() = runBlocking {
        val first = orders.prepare(owner,"PCT20")
        helper.writableDatabase.execSQL("UPDATE coupon_rules SET revision=1 WHERE code='PCT20'")
        orderError(OrderError.QUOTE_CHANGED) {orders.create(owner,first)}
        val second = orders.prepare(owner,"PCT20")
        assertNotEquals(first.id,second.id);assertEquals(1L,second.coupon!!.rule.revision)
        helper.writableDatabase.execSQL("UPDATE coupon_rules SET maximum_discount=10000 WHERE code='PCT20'")
        orderError(OrderError.QUOTE_CHANGED) {orders.create(owner,second)}
        assertTrue(orders.list(owner).isEmpty());assertEquals(30,stock())
        val third = orders.prepare(owner,"PCT20")
        assertEquals(566_000L,third.totalVnd)
        assertEquals(10_000L,orders.create(owner,third).discountVnd)
    }
    @Test fun differentRulesWithSameMoneyHaveDifferentIdentityAndOldRequestCannotBeUsed() = runBlocking {
        val a = orders.prepare(owner,"PCT20")
        val b = orders.prepare(owner,"FIX50")
        assertEquals(a.totalVnd,b.totalVnd);assertNotEquals(a.fingerprint,b.fingerprint);assertNotEquals(a.id,b.id)
        assertEquals(b.id,orders.prepare(owner," fix50 ").id)
        orderError(OrderError.REQUEST_CONFLICT) {orders.create(owner,a)}
        assertEquals("FIX50",orders.create(owner,b).couponCode)
    }
    @Test fun concurrentRetriesDeductStockAndWriteAuditOnceWithDiscount() = runBlocking {
        val request = orders.prepare(owner,"PCT20")
        val results = coroutineScope {(1..4).map {async(Dispatchers.IO) {orders.create(owner,request)}}.awaitAll()}
        assertEquals(1,results.map {it.id}.distinct().size);assertEquals(27,stock())
        assertEquals(1,orders.list(owner).size);assertTrue(results.all {it.discountVnd == 50_000L && it.totalVnd == 526_000L})
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM order_events WHERE order_id=?",arrayOf("${results.first().id}")).use {it.moveToFirst();assertEquals(1,it.getInt(0))}
    }
    @Test fun failedAuditRollsBackOrderDiscountStockAndCart() = runBlocking {
        val request = orders.prepare(owner,"PCT20")
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_coupon_audit BEFORE INSERT ON order_events BEGIN SELECT RAISE(ABORT,'test failure'); END")
        try {orders.create(owner,request);fail("Expected rollback")} catch (_: android.database.sqlite.SQLiteException) { }
        assertTrue(orders.list(owner).isEmpty());assertEquals(30,stock());assertEquals(3,cart.load(owner).quantity)
        helper.writableDatabase.execSQL("DROP TRIGGER fail_coupon_audit")
        assertEquals(50_000L,orders.create(owner,request).discountVnd)
    }
    @Test fun disabledModeRejectsCouponAndHidesRulesButOrdinaryCheckoutStillWorks() = runBlocking {
        val normal = OrderRepository(helper,coupons = LocalCouponRepository(false) {clock.get()})
        assertTrue(normal.couponRules(owner).isEmpty())
        couponError(CouponError.DISABLED) {normal.prepare(owner,"PCT20")}
        val request = normal.prepare(owner,"")
        assertNull(request.coupon);assertEquals(576_000L,normal.create(owner,request).totalVnd)
    }
    @Test fun inactiveFutureAndMinimumFailuresDoNotCreateOrders() = runBlocking {
        couponError(CouponError.NOT_FOUND) {orders.prepare(owner,"UNKNOWN")}
        helper.writableDatabase.execSQL("UPDATE coupon_rules SET active=0 WHERE code='PCT20'")
        couponError(CouponError.INACTIVE) {orders.prepare(owner,"PCT20")}
        helper.writableDatabase.execSQL("UPDATE coupon_rules SET active=1,starts_at=? WHERE code='PCT20'",arrayOf(clock.get()+1000))
        couponError(CouponError.NOT_STARTED) {orders.prepare(owner,"PCT20")}
        clock.addAndGet(1000)
        helper.writableDatabase.execSQL("UPDATE coupon_rules SET minimum_subtotal=1000000 WHERE code='PCT20'")
        couponError(CouponError.MINIMUM) {orders.prepare(owner,"PCT20")}
        assertTrue(orders.list(owner).isEmpty());assertEquals(30,stock())
    }
    @Test fun committedPayloadRecoversAfterReopenEvenIfCodeIsGoneAndModeDisabled() = runBlocking {
        val request = orders.prepare(owner,"PCT20");val order = orders.create(owner,request)
        helper.writableDatabase.execSQL("DELETE FROM coupon_rules");helper.close()
        helper = AuthDatabaseHelper(context,DB)
        orders = OrderRepository(helper,coupons = LocalCouponRepository(false) {Long.MAX_VALUE})
        val recovered = orders.recover(owner)!!
        assertEquals(request,recovered.request);assertEquals(order,recovered.order)
        assertEquals(order,orders.create(owner,recovered.request));assertEquals(27,stock())
    }
    @Test fun tamperedQuoteAndUnsupportedPayloadCannotBypassConfirmation() = runBlocking {
        val request = orders.prepare(owner,"PCT20")
        orderError(OrderError.REQUEST_CONFLICT) {orders.create(owner,request.copy(coupon = request.coupon!!.copy(discountVnd = 1)))}
        orderError(OrderError.REQUEST_CONFLICT) {orders.create(owner,request.copy(quotedFeeVnd = 1))}
        helper.writableDatabase.execSQL("UPDATE checkout_requests SET payload=? WHERE user_id=?",arrayOf("{\"version\":99}",owner))
        orderError(OrderError.INVALID_REQUEST) {orders.recover(owner)}
        assertTrue(orders.list(owner).isEmpty());assertEquals(30,stock())
    }
    @Test fun staleSessionCannotReadRulesOrPriceOwnersCart() = runBlocking {
        val request = orders.prepare(owner,"PCT20")
        val auth = AuthRepository(helper)
        val other = (auth.register("Khách khác","other@coupon.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        orderError(OrderError.SESSION) {orders.couponRules(owner)}
        orderError(OrderError.SESSION) {orders.prepare(owner,"PCT20")}
        orderError(OrderError.REQUEST_CONFLICT) {orders.create(other,request)}
        auth.login("coupon@bloomy.test",PASSWORD)
        assertTrue(orders.list(owner).isEmpty());assertEquals(3,cart.load(owner).quantity);assertEquals(30,stock())
    }
    companion object {private const val DB = "coupon-orders-test.db";private const val PASSWORD = "CouponTest123!"}
}

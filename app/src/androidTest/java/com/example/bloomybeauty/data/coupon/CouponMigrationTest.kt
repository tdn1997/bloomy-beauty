package com.example.bloomybeauty.data.coupon

import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.feature.cart.CartStep
import com.example.bloomybeauty.feature.cart.CartViewModel
import com.example.bloomybeauty.fixtures.LegacyOrders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class CouponMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var orders: OrderRepository
    private lateinit var cart: CartRepository
    private var userId = 0L
    private val store = ViewModelStore()
    @Before fun setup(): Unit = runBlocking {
        context.deleteDatabase(DB);helper = AuthDatabaseHelper(context,DB)
        userId = (AuthRepository(helper).register("Khách nâng cấp","migration@coupon.test","CouponTest123!","CouponTest123!") as AuthResult.Success).user.id
        cart = CartRepository(helper);orders = OrderRepository(helper)
        cart.add(userId,"cleanser");cart.setQuantity(userId,"cleanser",3)
        cart.saveShipping(userId,ShippingDetails("Người nhận","0912345678","123 Nguyễn Huệ, Thành phố Hồ Chí Minh"))
    }
    @After fun cleanup() {instrumentation.runOnMainSync {store.clear()};helper.close();context.deleteDatabase(DB)}
    private fun legacy(request: CheckoutRequest, committed: Boolean): CheckoutRequest {
        val old = request.copy(coupon = null,version = 1,quotedFeeVnd = null)
        val payload = JSONObject().apply {
            put("recipient",old.shipping.recipient);put("phone",old.shipping.phone);put("address",old.shipping.address)
            put("lines",JSONArray().apply {old.lines.forEach {line -> put(JSONObject().apply {
                put("id",line.productId);put("quantity",line.quantity);put("price",line.unitPriceVnd)
            })}})
        }.toString()
        helper.writableDatabase.execSQL("UPDATE checkout_requests SET fingerprint=?,payload=? WHERE user_id=?",arrayOf(old.fingerprint,payload,userId))
        if(committed) helper.writableDatabase.execSQL("UPDATE orders SET fingerprint=? WHERE request_id=?",arrayOf(old.fingerprint,old.id))
        helper.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(DB).path,null,SQLiteDatabase.OPEN_READWRITE).use {db ->
            db.execSQL("DROP TABLE user_notifications");db.execSQL("DROP TABLE coupon_rules");LegacyOrders.rebuild(db,true);db.version = 9
        }
        helper = AuthDatabaseHelper(context,DB);orders = OrderRepository(helper);cart = CartRepository(helper)
        assertEquals(AuthContract.DATABASE_VERSION,helper.writableDatabase.version)
        helper.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use {assertFalse(it.moveToFirst())}
        helper.readableDatabase.rawQuery("PRAGMA integrity_check",null).use {it.moveToFirst();assertEquals("ok",it.getString(0))}
        return old
    }
    @Test fun committedLegacyRequestReplaysSameOrderWithZeroDiscountAfterUpgrade() = runBlocking {
        val request = orders.prepare(userId);val order = orders.create(userId,request)
        cart.add(userId,"serum")
        val old = legacy(request,true)
        val recovery = orders.recover(userId)!!
        assertEquals(1,recovery.request.version);assertEquals(old.fingerprint,recovery.request.fingerprint)
        assertEquals(order,recovery.order);assertEquals(0L,recovery.order!!.discountVnd)
        assertEquals(order,orders.create(userId,recovery.request))
        assertEquals("serum",cart.load(userId).items.single().product.id)
        assertEquals(1,orders.list(userId).size)
    }
    @Test fun uncommittedLegacyRequiresNewQuoteAndExplicitConfirmation() = runBlocking {
        val old = legacy(orders.prepare(userId),false)
        try {orders.create(userId,old);fail("Legacy request must be confirmed again")} catch(e: OrderException) {assertEquals(OrderError.QUOTE_CHANGED,e.error)}
        lateinit var vm: CartViewModel
        instrumentation.runOnMainSync {vm = CartViewModel(cart,userId,"Khách nâng cấp",orders);store.put("cart",vm)}
        withTimeout(10000) {vm.state.first {!it.loading && !it.busy}}
        val state = vm.state.value
        assertEquals(CartStep.REVIEW,state.step);assertTrue(state.reviewNotice)
        assertEquals(2,state.request!!.version);assertNotEquals(old.id,state.request.id)
        assertTrue(orders.list(userId).isEmpty());assertEquals(3,cart.load(userId).quantity)
        instrumentation.runOnMainSync {vm.placeOrder()}
        withTimeout(10000) {vm.state.first {it.placedOrder != null && !it.busy}}
        assertEquals(1,orders.list(userId).size);assertEquals(0L,vm.state.value.placedOrder!!.discountVnd)
    }
    companion object {private const val DB = "coupon-migration-test.db"}
}

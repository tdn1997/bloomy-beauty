package com.example.bloomybeauty.data.order

import android.database.sqlite.SQLiteException
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.coupon.LocalCouponRepository
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class ReorderRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var auth: AuthRepository
    private lateinit var cart: CartRepository
    private lateinit var orders: OrderRepository
    private lateinit var reorder: ReorderRepository
    private var userId = 0L

    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB)
        helper = AuthDatabaseHelper(context, DB)
        auth = AuthRepository(helper); cart = CartRepository(helper)
        orders = OrderRepository(helper); reorder = orders.reorder
        userId = (auth.register("Khách mua lại", EMAIL, PASSWORD, PASSWORD) as AuthResult.Success).user.id
    }
    @After fun cleanup() { helper.close(); context.deleteDatabase(DB) }

    private suspend fun oldOrder(quantity: Int = 3, second: Boolean = false, delivered: Boolean = false): CustomerOrder {
        cart.add(userId, "cleanser"); cart.setQuantity(userId, "cleanser", quantity)
        if (second) cart.add(userId, "lip_balm")
        cart.saveShipping(userId, SHIPPING)
        val request = orders.prepare(userId)
        val order = orders.create(userId, request)
        if (delivered) helper.writableDatabase.execSQL("UPDATE orders SET status='DELIVERED' WHERE _id=?", arrayOf(order.id))
        else orders.cancel(userId, order.id)
        orders.acknowledge(userId, request.id)
        return orders.detail(userId, order.id)!!
    }

    @Test fun previewDoesNotWriteAndConfirmMergesAtCurrentPriceWithoutChangingHistoryOrStock() = runBlocking {
        val order = oldOrder()
        cart.add(userId, "lip_balm")
        helper.writableDatabase.execSQL("UPDATE products SET price_vnd=200000 WHERE id='cleanser'")
        val preview = reorder.preview(userId, order.id)
        assertEquals(192000L, preview.lines.single().oldPriceVnd)
        assertEquals(200000L, preview.lines.single().currentPriceVnd)
        assertEquals(600000L, preview.subtotalVnd)
        assertEquals(1, cart.load(userId).quantity)
        reorder.confirm(userId, preview)
        assertEquals(4, cart.load(userId).quantity)
        assertEquals(200000L, cart.load(userId).items.first { it.product.id == "cleanser" }.acceptedPriceVnd)
        assertEquals(order, orders.detail(userId, order.id))
        assertEquals(30, stock("cleanser")); assertEquals(1, orders.list(userId).size)
        assertEquals(SHIPPING, cart.load(userId).shipping)
        helper.close(); helper = AuthDatabaseHelper(context, DB)
        assertEquals(4, CartRepository(helper).load(userId).quantity)
    }

    @Test fun mergesExistingQuantityAndAddsOnlyAvailableStock() = runBlocking {
        val order = oldOrder()
        cart.add(userId, "cleanser")
        helper.writableDatabase.execSQL("UPDATE products SET stock=2 WHERE id='cleanser'")
        val preview = reorder.preview(userId, order.id)
        assertEquals(1, preview.addedQuantity); assertEquals(ReorderReason.PARTIAL, preview.lines.single().reason)
        reorder.confirm(userId, preview)
        assertEquals(2, cart.load(userId).quantity); assertEquals(2, stock("cleanser"))
    }

    @Test fun quantityCapNeverExceeds99AndExistingInvalidCartIsPreserved() = runBlocking {
        val order = oldOrder()
        helper.writableDatabase.execSQL("UPDATE products SET stock=200 WHERE id='cleanser'")
        cart.add(userId, "cleanser"); cart.setQuantity(userId, "cleanser", 98)
        val preview = reorder.preview(userId, order.id)
        assertEquals(1, preview.addedQuantity)
        reorder.confirm(userId, preview)
        assertEquals(99, cart.load(userId).quantity)
        assertEquals(ReorderReason.LIMIT, reorder.preview(userId, order.id).lines.single().reason)
        helper.writableDatabase.execSQL("UPDATE products SET stock=2 WHERE id='cleanser'")
        val invalid = reorder.preview(userId, order.id)
        assertEquals(ReorderReason.CART_INVALID, invalid.lines.single().reason)
        assertFalse(invalid.canConfirm); assertEquals(99, cart.load(userId).quantity)
    }

    @Test fun inactiveAndOutOfStockLinesAreExplainedAndCannotBeConfirmed() = runBlocking {
        val order = oldOrder(second = true)
        helper.writableDatabase.execSQL("UPDATE products SET active=0 WHERE id='cleanser'")
        helper.writableDatabase.execSQL("UPDATE products SET stock=0 WHERE id='lip_balm'")
        val preview = reorder.preview(userId, order.id)
        assertEquals(listOf(ReorderReason.INACTIVE, ReorderReason.OUT_OF_STOCK), preview.lines.map { it.reason })
        assertFalse(preview.canConfirm)
        expect(ReorderError.EMPTY) { reorder.confirm(userId, preview) }
        assertTrue(cart.load(userId).items.isEmpty())
    }

    @Test fun missingLegacyProductUsesHistoricalNameAndOtherLineStillAdds() = runBlocking {
        val order = oldOrder(second = true)
        // Simulate an imported legacy order with a dangling product reference.
        helper.writableDatabase.execSQL("PRAGMA foreign_keys=OFF")
        try { helper.writableDatabase.delete("products", "id='cleanser'", null) }
        finally { helper.writableDatabase.execSQL("PRAGMA foreign_keys=ON") }
        val preview = reorder.preview(userId, order.id)
        val missing = preview.lines.first()
        assertEquals(ReorderReason.MISSING, missing.reason)
        assertEquals(order.lines.first().name, missing.name)
        reorder.confirm(userId, preview)
        assertEquals("lip_balm", cart.load(userId).items.single().product.id)
    }

    @Test fun existingUnacceptedPriceRequiresCartConfirmationFirst() = runBlocking {
        val order = oldOrder()
        cart.add(userId, "cleanser")
        helper.writableDatabase.execSQL("UPDATE products SET price_vnd=180000 WHERE id='cleanser'")
        val preview = reorder.preview(userId, order.id)
        assertTrue(preview.needsPriceAcceptance); assertFalse(preview.canConfirm)
        expect(ReorderError.EMPTY) { reorder.confirm(userId, preview) }
        cart.acceptCurrentPrices(userId, mapOf("cleanser" to 180000L))
        reorder.confirm(userId, reorder.preview(userId, order.id))
        assertEquals(4, cart.load(userId).quantity)
    }

    @Test fun priceStockStatusAndCartChangesAfterPreviewRequireReconfirmation() = runBlocking {
        val order = oldOrder()
        for (sql in listOf(
            "UPDATE products SET price_vnd=200000 WHERE id='cleanser'",
            "UPDATE products SET stock=2 WHERE id='cleanser'",
            "UPDATE products SET active=0 WHERE id='cleanser'",
            "UPDATE products SET active=1,stock=30 WHERE id='cleanser'",
        )) {
            val preview = reorder.preview(userId, order.id)
            helper.writableDatabase.execSQL(sql)
            expect(ReorderError.CHANGED) { reorder.confirm(userId, preview) }
            assertTrue(cart.load(userId).items.isEmpty())
        }
        val preview = reorder.preview(userId, order.id)
        cart.add(userId, "lip_balm")
        expect(ReorderError.CHANGED) { reorder.confirm(userId, preview) }
        assertEquals(1, cart.load(userId).quantity)
    }

    @Test fun committedCheckoutIsPreservedUntilAcknowledged() = runBlocking {
        val order = oldOrder()
        cart.add(userId, "lip_balm")
        val request = orders.prepare(userId)
        val committed = orders.create(userId, request)
        expect(ReorderError.CHECKOUT_PENDING) { reorder.preview(userId, order.id) }
        assertEquals(committed.id, orders.recover(userId)?.order?.id)
        orders.acknowledge(userId, request.id)
        assertTrue(reorder.preview(userId, order.id).canConfirm)
    }

    @Test fun draftCheckoutClearsOnlyOnSuccessfulMergeAndNewCheckoutHasNewPricesAndNoOldCoupon() = runBlocking {
        val order = oldOrder(delivered = true)
        orders = OrderRepository(helper, coupons = LocalCouponRepository(enabled = true))
        helper.writableDatabase.execSQL("INSERT INTO coupon_rules VALUES('REORDER10K','FIXED',10000,0,NULL,0,9223372036854775807,1,0)")
        cart.add(userId, "lip_balm")
        val request = orders.prepare(userId, "REORDER10K")
        assertNotNull(request.coupon)
        val preview = reorder.preview(userId, order.id)
        assertEquals(request.id, orders.recover(userId)?.request?.id)
        reorder.confirm(userId, preview)
        assertNull(orders.recover(userId))
        val newRequest = orders.prepare(userId)
        assertNotEquals(request.id, newRequest.id); assertNull(newRequest.coupon)
        val newOrder = orders.create(userId, newRequest)
        assertNotEquals(order.id, newOrder.id); assertEquals(4, newOrder.lines.sumOf { it.quantity })
        assertEquals(order, orders.detail(userId, order.id))
    }

    @Test fun failureOnSecondWriteRollsBackFirstLineAndCheckoutDraft() = runBlocking {
        val order = oldOrder(second = true)
        cart.add(userId, "toner")
        val request = orders.prepare(userId)
        val preview = reorder.preview(userId, order.id)
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_reorder BEFORE INSERT ON cart_items WHEN NEW.product_id='lip_balm' BEGIN SELECT RAISE(ABORT,'test failure'); END")
        try { reorder.confirm(userId, preview); fail("Expected SQLite failure") } catch (_: SQLiteException) { }
        assertEquals("toner", cart.load(userId).items.single().product.id)
        assertEquals(request.id, orders.recover(userId)?.request?.id)
        assertEquals(30, stock("cleanser"))
        helper.writableDatabase.execSQL("DROP TRIGGER fail_reorder")
        reorder.confirm(userId, preview)
        assertEquals(5, cart.load(userId).quantity); assertNull(orders.recover(userId))
    }

    @Test fun repeatedAndConcurrentConfirmAddsOnceEvenWithTwoSeparatePreviews() = runBlocking {
        val order = oldOrder()
        val preview = reorder.preview(userId, order.id)
        val other = reorder.preview(userId, order.id)
        val results = coroutineScope { (1..8).map { index -> async(Dispatchers.IO) {
            try { reorder.confirm(userId, if (index % 2 == 0) preview else other); true }
            catch (e: ReorderException) { assertTrue(e.error in listOf(ReorderError.USED, ReorderError.CHANGED)); false }
        } }.awaitAll() }
        assertEquals(1, results.count { it }); assertEquals(3, cart.load(userId).quantity)
    }

    @Test fun ownersSessionsStatusAndMissingOrdersAreCheckedAgainAtConfirmation() = runBlocking {
        val order = oldOrder()
        val preview = reorder.preview(userId, order.id)
        val other = (auth.register("Khách khác", "other-reorder@test.com", PASSWORD, PASSWORD) as AuthResult.Success).user.id
        expect(ReorderError.SESSION) { reorder.confirm(userId, preview) }
        expect(ReorderError.SESSION) { reorder.confirm(other, preview) }
        expect(ReorderError.NOT_FOUND) { reorder.preview(other, order.id) }
        expect(ReorderError.NOT_FOUND) { reorder.preview(other, Long.MAX_VALUE) }
        auth.login(EMAIL, PASSWORD)
        for (status in listOf("PENDING", "CONFIRMED", "SHIPPING")) {
            helper.writableDatabase.execSQL("UPDATE orders SET status=? WHERE _id=?", arrayOf(status, order.id))
            expect(ReorderError.STATUS) { reorder.preview(userId, order.id) }
            expect(ReorderError.STATUS) { reorder.confirm(userId, preview) }
        }
        assertTrue(cart.load(userId).items.isEmpty())
    }

    @Test fun changedCheckoutDraftAndOverflowRejectWithoutWrites() = runBlocking {
        val order = oldOrder()
        cart.add(userId, "lip_balm")
        val preview = reorder.preview(userId, order.id)
        orders.prepare(userId)
        expect(ReorderError.CHANGED) { reorder.confirm(userId, preview) }
        helper.writableDatabase.execSQL("UPDATE products SET price_vnd=? WHERE id='cleanser'", arrayOf(Long.MAX_VALUE))
        expect(ReorderError.TOTAL_OVERFLOW) { reorder.preview(userId, order.id) }
        assertEquals(1, cart.load(userId).quantity)
    }

    @Test fun failureInvalidatingCheckoutRollsBackAllMergedItems() = runBlocking {
        val order = oldOrder(second = true)
        cart.add(userId, "toner")
        val request = orders.prepare(userId)
        val preview = reorder.preview(userId, order.id)
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_draft_delete BEFORE DELETE ON checkout_requests BEGIN SELECT RAISE(ABORT,'test failure'); END")
        try { reorder.confirm(userId, preview); fail("Expected rollback") } catch (_: SQLiteException) { }
        assertEquals("toner", cart.load(userId).items.single().product.id)
        assertEquals(request.id, orders.recover(userId)?.request?.id)
        assertEquals(30, stock("cleanser")); assertEquals(30, stock("lip_balm"))
    }

    private fun stock(id: String) = helper.readableDatabase.rawQuery("SELECT stock FROM products WHERE id=?", arrayOf(id)).use { it.moveToFirst(); it.getInt(0) }
    private suspend fun expect(error: ReorderError, block: suspend () -> Unit) {
        try { block(); fail("Expected $error") } catch (e: ReorderException) { assertEquals(error, e.error) }
    }
    companion object {
        private const val DB = "reorder-repository-test.db"
        private const val EMAIL = "reorder@test.com"
        private const val PASSWORD = "ReorderTest123!"
        private val SHIPPING = ShippingDetails("Khách mua lại", "0912345678", "123 Nguyễn Huệ, Thành phố Hồ Chí Minh")
    }
}

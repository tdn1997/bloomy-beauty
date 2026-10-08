package com.example.bloomybeauty.data.order

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.catalog.CatalogSchema
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OrderRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var cart: CartRepository
    private lateinit var orders: OrderRepository
    private lateinit var auth: AuthRepository
    private var userId = 0L

    @Before fun setUp() = runBlocking {
        context.deleteDatabase(DATABASE)
        helper = AuthDatabaseHelper(context, DATABASE)
        cart = CartRepository(helper); orders = OrderRepository(helper); auth = AuthRepository(helper)
        userId = (auth.register("Nguyễn Thị Mỹ", "order@example.com", PASSWORD, PASSWORD) as AuthResult.Success).user.id
    }
    @After fun tearDown() { helper.close(); context.deleteDatabase(DATABASE) }

    private suspend fun checkout(quantity: Int = 1): CheckoutRequest {
        cart.add(userId, "cleanser")
        cart.setQuantity(userId, "cleanser", quantity)
        cart.saveShipping(userId, SHIPPING)
        return orders.prepare(userId)
    }

    @Test fun createKeepsSnapshotsClearsCartAndReplayLeavesNewCartUntouched() = runBlocking {
        val request = checkout(2)
        val order = orders.create(userId, request)
        assertEquals(384_000L, order.subtotalVnd)
        assertEquals(30_000L, order.feeVnd)
        assertEquals(414_000L, order.totalVnd)
        assertEquals("PENDING", order.status)
        assertEquals(28, stock("cleanser"))
        assertTrue(cart.load(userId).items.isEmpty())
        cart.add(userId, "lip_balm")
        helper.writableDatabase.execSQL("UPDATE products SET name = 'Tên đã đổi', price_vnd = 999000 WHERE id = 'cleanser'")
        assertEquals(order.id, orders.create(userId, request).id)
        assertEquals(28, stock("cleanser"))
        assertEquals("lip_balm", cart.load(userId).items.single().product.id)
        val history = orders.detail(userId, order.id)!!
        assertEquals("Gel bí đao rửa mặt", history.lines.single().name)
        assertEquals(192_000L, history.lines.single().unitPriceVnd)
        assertEquals(SHIPPING, history.shipping)
    }

    @Test fun simultaneousRetriesCreateOneOrderAndDeductOnce() = runBlocking {
        val request = checkout()
        val results = coroutineScope { (1..8).map { async(Dispatchers.IO) { orders.create(userId, request).id } }.awaitAll() }
        assertEquals(1, results.distinct().size)
        assertEquals(1, orders.list(userId).size)
        assertEquals(29, stock("cleanser"))
    }

    @Test fun shortageInOneLineRollsBackEntireOrderAndPreservesCart() = runBlocking {
        checkout()
        cart.add(userId, "lip_balm")
        val request = orders.prepare(userId)
        helper.writableDatabase.execSQL("UPDATE products SET stock = 0 WHERE id = 'lip_balm'")
        expectCart(CartError.STOCK) { orders.create(userId, request) }
        assertEquals(30, stock("cleanser"))
        assertTrue(orders.list(userId).isEmpty())
        assertEquals(2, cart.load(userId).quantity)
    }

    @Test fun insertFailureAfterFirstStockUpdateRollsBackAllWrites() = runBlocking {
        checkout()
        cart.add(userId, "lip_balm")
        val request = orders.prepare(userId)
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_second_line BEFORE INSERT ON order_items WHEN NEW.product_id = 'lip_balm' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { orders.create(userId, request); fail("Expected rollback") }
        catch (exception: android.database.sqlite.SQLiteException) { /* intentional failure after prior writes */ }
        assertEquals(30, stock("cleanser"))
        assertEquals(30, stock("lip_balm"))
        assertTrue(orders.list(userId).isEmpty())
        assertEquals(2, cart.load(userId).quantity)
        helper.writableDatabase.execSQL("DROP TRIGGER fail_second_line")
        assertEquals(2, orders.create(userId, request).lines.size)
    }

    @Test fun changedPriceStatusQuantityOrAddressRequireNewConfirmation() = runBlocking {
        val request = checkout()
        helper.writableDatabase.execSQL("UPDATE products SET price_vnd = 200000 WHERE id = 'cleanser'")
        expectCart(CartError.PRICE_CHANGED) { orders.create(userId, request) }
        cart.acceptCurrentPrices(userId, mapOf("cleanser" to 200_000L))
        expectOrder(OrderError.CART_CHANGED) { orders.create(userId, request) }
        val updated = orders.prepare(userId)
        assertNotEquals(request.id, updated.id)
        cart.setQuantity(userId, "cleanser", 2)
        expectOrder(OrderError.CART_CHANGED) { orders.create(userId, updated) }
        val changedQuantity = orders.prepare(userId)
        cart.saveShipping(userId, SHIPPING.copy(address = "Địa chỉ mới tại TP Hồ Chí Minh"))
        expectOrder(OrderError.CART_CHANGED) { orders.create(userId, changedQuantity) }
        val changedAddress = orders.prepare(userId)
        helper.writableDatabase.execSQL("UPDATE products SET active = 0 WHERE id = 'cleanser'")
        expectCart(CartError.UNAVAILABLE) { orders.create(userId, changedAddress) }
        assertTrue(orders.list(userId).isEmpty())
        assertEquals(30, stock("cleanser"))
    }

    @Test fun requestIdentityCannotBeReusedForDifferentContent() = runBlocking {
        val request = checkout()
        orders.create(userId, request)
        expectOrder(OrderError.REQUEST_CONFLICT) { orders.create(userId, request.copy(shipping = SHIPPING.copy(recipient = "Trần Mỹ"))) }
        assertEquals(1, orders.list(userId).size)
        assertEquals(29, stock("cleanser"))
    }

    @Test fun requestsAndCommittedResultsRecoverAfterDatabaseReopen() = runBlocking {
        val request = checkout()
        assertEquals(request.id, orders.prepare(userId).id)
        helper.close()
        helper = AuthDatabaseHelper(context, DATABASE)
        cart = CartRepository(helper); orders = OrderRepository(helper)
        assertEquals(request, orders.recover(userId)?.request)
        val order = orders.create(userId, request)
        helper.close()
        helper = AuthDatabaseHelper(context, DATABASE)
        orders = OrderRepository(helper)
        assertEquals(order.id, orders.recover(userId)?.order?.id)
        assertEquals(order.id, orders.create(userId, request).id)
        orders.acknowledge(userId, request.id)
        assertNull(orders.recover(userId))
    }

    @Test fun lastUnitCannotBeSoldTwiceAndOrdersStayPrivate() = runBlocking {
        helper.writableDatabase.execSQL("UPDATE products SET stock = 1 WHERE id = 'cleanser'")
        val first = checkout()
        val other = (auth.register("Trần Minh", "otherorder@example.com", PASSWORD, PASSWORD) as AuthResult.Success).user.id
        cart.add(other, "cleanser"); cart.saveShipping(other, SHIPPING)
        val second = orders.prepare(other)
        expectOrder(OrderError.SESSION) { orders.create(userId, first) }
        auth.login("order@example.com", PASSWORD)
        val order = orders.create(userId, first)
        auth.login("otherorder@example.com", PASSWORD)
        expectCart(CartError.STOCK) { orders.create(other, second) }
        assertEquals(0, stock("cleanser"))
        assertTrue(orders.list(other).isEmpty())
        assertNull(orders.detail(other, order.id))
        expectOrder(OrderError.REQUEST_CONFLICT) { orders.create(other, first) }
    }

    @Test fun versionThreeMigrationKeepsSessionCartAddressAndFavorites() = runBlocking {
        helper.close(); context.deleteDatabase(DATABASE)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DATABASE), null).use { db ->
            db.execSQL("CREATE TABLE users (_id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT UNIQUE NOT NULL, password_hash BLOB NOT NULL, password_salt BLOB NOT NULL, password_iterations INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE auth_session (_id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(_id))")
            val digest = PasswordHasher().hash(PASSWORD)
            db.execSQL("INSERT INTO users VALUES (?, ?, ?, ?, ?, ?)", arrayOf(1, "Nguyễn Thị Mỹ", "order@example.com", digest.hash, digest.salt, digest.iterations))
            db.execSQL("INSERT INTO auth_session VALUES (1, 1)")
            CatalogSchema.create(db); CartSchema.upgradeFromCatalog(db)
            db.execSQL("INSERT INTO favorites VALUES (1, 'cleanser')")
            db.execSQL("INSERT INTO cart_items VALUES (1, 'cleanser', 2, 192000)")
            db.execSQL("INSERT INTO shipping_details VALUES (?, ?, ?, ?)", arrayOf(1, SHIPPING.recipient, SHIPPING.phone, SHIPPING.address))
            db.version = 3
        }
        helper = AuthDatabaseHelper(context, DATABASE)
        orders = OrderRepository(helper); cart = CartRepository(helper)
        assertEquals("Nguyễn Thị Mỹ", AuthRepository(helper).restoreSession()?.name)
        assertEquals(setOf("cleanser"), CatalogRepository(helper).load(1).favoriteIds)
        val order = orders.create(1, orders.prepare(1))
        assertEquals(2, order.lines.single().quantity)
        assertEquals(SHIPPING, order.shipping)
        assertEquals(AuthContract.DATABASE_VERSION, helper.readableDatabase.version)
    }

    private fun stock(id: String): Int = helper.readableDatabase.rawQuery("SELECT stock FROM products WHERE id = ?", arrayOf(id)).use { it.moveToFirst(); it.getInt(0) }

    @Test fun repeatedAndConcurrentCancellationRestoresStockOnceAndCannotRecreateOrder() = runBlocking {
        val request = checkout(2)
        val order = orders.create(userId, request)
        cart.add(userId, "lip_balm")
        val results = coroutineScope { (1..8).map { async(Dispatchers.IO) { orders.cancel(userId, order.id) } }.awaitAll() }
        assertTrue(results.all { it.status == "CANCELLED" })
        assertEquals(30, stock("cleanser"))
        assertEquals("CANCELLED", orders.create(userId, request).status)
        assertEquals(30, stock("cleanser"))
        assertEquals("lip_balm", cart.load(userId).items.single().product.id)
        assertEquals(order.shipping, orders.detail(userId, order.id)?.shipping)
        helper.close()
        helper = AuthDatabaseHelper(context, DATABASE)
        orders = OrderRepository(helper)
        assertEquals("CANCELLED", orders.cancel(userId, order.id).status)
        assertEquals(30, stock("cleanser"))
    }

    @Test fun processedOrdersCannotBeCancelled() = runBlocking {
        val order = orders.create(userId, checkout())
        for (status in listOf("CONFIRMED", "SHIPPING", "DELIVERED")) {
            helper.writableDatabase.execSQL("UPDATE orders SET status = ? WHERE _id = ?", arrayOf(status, order.id))
            expectOrder(OrderError.CANNOT_CANCEL) { orders.cancel(userId, order.id) }
            assertEquals(status, orders.detail(userId, order.id)?.status)
            assertEquals(29, stock("cleanser"))
        }
    }

    @Test fun cancellationRequiresActiveSessionAndOwnership() = runBlocking {
        val order = orders.create(userId, checkout())
        val other = (auth.register("Trần Minh", "cancelother@example.com", PASSWORD, PASSWORD) as AuthResult.Success).user.id
        expectOrder(OrderError.SESSION) { orders.cancel(userId, order.id) }
        expectOrder(OrderError.NOT_FOUND) { orders.cancel(other, order.id) }
        expectOrder(OrderError.NOT_FOUND) { orders.cancel(other, 999999) }
        auth.login("order@example.com",PASSWORD)
        assertEquals("PENDING", orders.detail(userId, order.id)?.status)
        assertEquals(29, stock("cleanser"))
    }

    @Test fun failureDuringStockRestorationRollsBackStatusAndEveryLine() = runBlocking {
        checkout(2)
        cart.add(userId, "lip_balm")
        val order = orders.create(userId, orders.prepare(userId))
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE UPDATE OF stock ON products WHEN OLD.id = 'lip_balm' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { orders.cancel(userId, order.id); fail("Expected rollback") }
        catch (exception: android.database.sqlite.SQLiteException) { /* deliberately fail after restoring the first line */ }
        assertEquals("PENDING", orders.detail(userId, order.id)?.status)
        assertEquals(28, stock("cleanser"))
        assertEquals(29, stock("lip_balm"))
        helper.writableDatabase.execSQL("DROP TRIGGER fail_restore")
        orders.cancel(userId, order.id)
        assertEquals(30, stock("cleanser")); assertEquals(30, stock("lip_balm"))
    }

    @Test fun stockOverflowBlocksCancellationWithoutChangingStatus() = runBlocking {
        val order = orders.create(userId, checkout())
        helper.writableDatabase.execSQL("UPDATE products SET stock = ? WHERE id = 'cleanser'", arrayOf(Int.MAX_VALUE))
        expectOrder(OrderError.STOCK_OVERFLOW) { orders.cancel(userId, order.id) }
        assertEquals("PENDING", orders.detail(userId, order.id)?.status)
        assertEquals(Int.MAX_VALUE, stock("cleanser"))
    }

    @Test fun cancellationAndConditionalConfirmationCannotBothWin() = runBlocking {
        val order = orders.create(userId, checkout())
        coroutineScope {
            val cancellation = async(Dispatchers.IO) {
                try { orders.cancel(userId, order.id) }
                catch (exception: OrderException) { assertEquals(OrderError.CANNOT_CANCEL, exception.error) }
            }
            val confirmation = async(Dispatchers.IO) {
                helper.writableDatabase.execSQL("UPDATE orders SET status = 'CONFIRMED' WHERE _id = ? AND status = 'PENDING'", arrayOf(order.id))
            }
            cancellation.await(); confirmation.await()
        }
        val result = orders.detail(userId, order.id)!!
        assertTrue(result.status in listOf("CONFIRMED", "CANCELLED"))
        assertEquals(if (result.status == "CANCELLED") 30 else 29, stock("cleanser"))
    }

    @Test fun hiddenProductsStillReceiveRestoredStockWithoutBecomingVisible() = runBlocking {
        val order = orders.create(userId, checkout())
        helper.writableDatabase.execSQL("UPDATE products SET active = 0 WHERE id = 'cleanser'")
        orders.cancel(userId, order.id)
        assertEquals(30, stock("cleanser"))
        assertFalse(CatalogRepository(helper).load(userId).products.any { it.id == "cleanser" })
    }
    private suspend fun expectCart(error: CartError, block: suspend () -> Unit) {
        try { block(); fail("Expected $error") } catch (exception: CartException) { assertEquals(error, exception.error) }
    }
    private suspend fun expectOrder(error: OrderError, block: suspend () -> Unit) {
        try { block(); fail("Expected $error") } catch (exception: OrderException) { assertEquals(error, exception.error) }
    }
    private companion object {
        const val DATABASE = "order_repository_test.db"
        const val PASSWORD = "password123"
        val SHIPPING = ShippingDetails("Nguyễn Thị Mỹ", "0912345678", "12 Nguyễn Văn A, TP Hồ Chí Minh")
    }
}

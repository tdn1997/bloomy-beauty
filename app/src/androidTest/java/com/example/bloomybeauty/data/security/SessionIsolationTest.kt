package com.example.bloomybeauty.data.security

import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.data.admin.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class SessionIsolationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var auth: AuthRepository
    private lateinit var cart: CartRepository
    private lateinit var catalog: CatalogRepository
    private lateinit var orders: OrderRepository
    private var owner = 0L
    private lateinit var request: CheckoutRequest
    private lateinit var order: CustomerOrder

    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB)
        helper = AuthDatabaseHelper(context, DB)
        auth = AuthRepository(helper); cart = CartRepository(helper)
        catalog = CatalogRepository(helper); orders = OrderRepository(helper)
        owner = (auth.register("Nguyễn Thị Mỹ", EMAIL, PASSWORD, PASSWORD) as AuthResult.Success).user.id
        cart.add(owner, "cleanser")
        cart.saveShipping(owner, SHIPPING)
        request = orders.prepare(owner); order = orders.create(owner, request)
        cart.add(owner, "lip_balm"); catalog.toggleFavorite(owner, "cleanser")
    }
    @After fun cleanup() { helper.close(); context.deleteDatabase(DB) }

    private suspend fun deniedCartOperations() {
        val operations: List<suspend () -> Unit> = listOf(
            { cart.load(owner); Unit }, { cart.add(owner, "cleanser") },
            { cart.setQuantity(owner, "lip_balm", 2) }, { cart.remove(owner, "lip_balm") },
            { cart.acceptCurrentPrices(owner, mapOf("lip_balm" to 32000L)) },
            { cart.validateForShipping(owner); Unit }, { cart.saveShipping(owner, SHIPPING.copy(recipient="Tên khác")); Unit },
        )
        for (operation in operations) {
            try { operation(); fail("Cart must reject another or missing session") }
            catch (e: CartException) { assertEquals(CartError.SESSION, e.error) }
        }
    }
    private suspend fun deniedOrderOperations() {
        val operations: List<suspend () -> Unit> = listOf(
            { orders.list(owner); Unit }, { orders.detail(owner, order.id); Unit },
            { orders.acknowledge(owner, request.id) }, { orders.recover(owner); Unit },
            { orders.cancel(owner, order.id); Unit }, { orders.create(owner, request); Unit },
            { orders.prepare(owner); Unit },
        )
        for (operation in operations) {
            try { operation(); fail("Orders must reject another or missing session") }
            catch (e: OrderException) { assertEquals(OrderError.SESSION, e.error) }
        }
    }
    private suspend fun deniedFavorites() {
        try { catalog.load(owner); fail("Private favorites must require owner's session") } catch (_: SessionException) { }
        try { catalog.toggleFavorite(owner, "cleanser"); fail("Favorite write must require owner's session") } catch (_: SessionException) { }
    }
    private suspend fun unchangedOwnerData() {
        auth.login(EMAIL, PASSWORD)
        assertEquals("lip_balm", cart.load(owner).items.single().product.id)
        assertEquals(SHIPPING, cart.load(owner).shipping)
        assertEquals(setOf("cleanser"), catalog.load(owner).favoriteIds)
        assertEquals(order, orders.detail(owner, order.id))
        assertEquals(request.id, orders.recover(owner)?.request?.id)
        assertEquals(29, catalog.load(owner).products.first { it.id == "cleanser" }.stock)
    }
    @Test fun loggedOutCallsCannotReadOrMutatePrivateData() = runBlocking {
        auth.logout(); deniedCartOperations(); deniedOrderOperations(); deniedFavorites(); unchangedOwnerData()
    }
    @Test fun otherCustomerCannotImpersonateOwnerOrAcknowledgeTheirRequest() = runBlocking {
        val other = (auth.register("Trần Minh", "isolation-other@example.com", PASSWORD, PASSWORD) as AuthResult.Success).user.id
        deniedCartOperations(); deniedOrderOperations(); deniedFavorites()
        assertTrue(cart.load(other).items.isEmpty()); assertTrue(orders.list(other).isEmpty())
        assertNull(orders.detail(other, order.id)); unchangedOwnerData()
    }
    @Test fun customerCannotWriteCatalogOrOrderStatusThroughAdminApi() = runBlocking {
        val admin = AdminRepository(helper)
        val product = catalog.load(owner).products.first()
        val operations: List<suspend () -> Unit> = listOf(
            { admin.load(owner); Unit }, { admin.save(owner, product.copy(stock=999), false); Unit },
            { admin.save(owner, product, true); Unit }, { admin.transition(owner, order.id, "PENDING", "CONFIRMED") },
        )
        for (operation in operations) {
            try { operation(); fail("Customer must not have administrator rights") }
            catch (e: AdminException) { assertEquals(AdminError.PERMISSION, e.error) }
        }
        unchangedOwnerData()
    }
    companion object {
        private const val DB = "session-isolation-test.db"
        private const val EMAIL = "isolation@example.com"
        private const val PASSWORD = "StrongPassword123"
        private val SHIPPING = ShippingDetails("Nguyễn Thị Mỹ", "0912345678", "123 Nguyễn Huệ, TP Hồ Chí Minh")
    }
}

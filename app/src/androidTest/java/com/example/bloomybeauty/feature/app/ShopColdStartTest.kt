package com.example.bloomybeauty.feature.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.feature.auth.AuthViewModel
import com.example.bloomybeauty.feature.cart.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

/** Discards all ViewModels and reopens SQLite; this does not kill an OS process. */
class ShopColdStartTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: AuthDatabaseHelper
    private var store = ViewModelStore()
    private var userId = 0L
    private lateinit var auth: AuthViewModel
    private lateinit var cart: CartViewModel
    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB); helper = AuthDatabaseHelper(context, DB)
        userId = (AuthRepository(helper).register("Nguyễn Thị Mỹ", "coldstart@example.com", PASSWORD, PASSWORD) as AuthResult.Success).user.id
        val repository = CartRepository(helper)
        repository.add(userId, "cleanser")
        repository.saveShipping(userId, ShippingDetails("Nguyễn Thị Mỹ", "0912345678", "123 Nguyễn Huệ, TP Hồ Chí Minh"))
        OrderRepository(helper).prepare(userId)
        startWithFreshMemory()
    }
    @After fun cleanup() { instrumentation.runOnMainSync { store.clear() }; helper.close(); context.deleteDatabase(DB) }
    private suspend fun startWithFreshMemory() {
        val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
        instrumentation.runOnMainSync { auth = ViewModelProvider(owner, AuthViewModel.Factory(AuthRepository(helper)))[AuthViewModel::class.java] }
        withTimeout(10000) { while (auth.state.value.initializing) delay(20) }
        assertEquals(userId, auth.state.value.user?.id)
        instrumentation.runOnMainSync {
            cart = ViewModelProvider(auth, CartViewModel.Factory(CartRepository(helper), userId, "Nguyễn Thị Mỹ", OrderRepository(helper)))[CartViewModel::class.java]
        }
        ready()
    }
    private suspend fun ready() { withTimeout(10000) { while (cart.state.value.loading || cart.state.value.busy) delay(20) } }
    private suspend fun discardMemoryAndReopen() {
        instrumentation.runOnMainSync { store.clear() }
        helper.close(); helper = AuthDatabaseHelper(context, DB); store = ViewModelStore()
        startWithFreshMemory()
    }
    @Test fun newViewModelsRecoverPendingAndCommittedRequestWithoutDuplicateOrder() = runBlocking {
        assertEquals(CartStep.REVIEW, cart.state.value.step)
        val oldAuth = auth; val oldCart = cart; val requestId = cart.state.value.request!!.id
        discardMemoryAndReopen()
        assertNotSame(oldAuth, auth); assertNotSame(oldCart, cart)
        assertEquals(CartStep.REVIEW, cart.state.value.step); assertEquals(requestId, cart.state.value.request!!.id)
        instrumentation.runOnMainSync { cart.placeOrder() }; ready()
        val orderId = cart.state.value.placedOrder!!.id
        // Newly added items must survive recovery/replay of the previously committed request.
        CartRepository(helper).add(userId, "lip_balm")
        discardMemoryAndReopen()
        assertEquals(CartStep.SUCCESS, cart.state.value.step); assertEquals(orderId, cart.state.value.placedOrder!!.id)
        assertEquals(requestId, cart.state.value.request!!.id)
        instrumentation.runOnMainSync { cart.placeOrder() }; ready()
        assertEquals(orderId, cart.state.value.placedOrder!!.id)
        assertEquals("lip_balm", cart.state.value.snapshot.items.single().product.id)
        assertEquals(1, OrderRepository(helper).list(userId).size)
        assertEquals(29, helper.readableDatabase.rawQuery("SELECT stock FROM products WHERE id='cleanser'", null).use { it.moveToFirst(); it.getInt(0) })
    }
    @Test fun revokedSessionClearsCartAndCheckoutInsteadOfCrashingDuringErrorRefresh() = runBlocking {
        assertEquals(CartStep.REVIEW, cart.state.value.step)
        AuthRepository(helper).logout()
        instrumentation.runOnMainSync { cart.reload() }; ready()
        assertEquals(CartError.SESSION, cart.state.value.error)
        assertEquals(CartStep.ITEMS, cart.state.value.step)
        assertTrue(cart.state.value.snapshot.items.isEmpty())
        assertEquals(ShippingDetails(), cart.state.value.draft)
        assertNull(cart.state.value.request); assertNull(cart.state.value.placedOrder)
        // Rejected reads do not acknowledge the owner's pending request.
        AuthRepository(helper).login("coldstart@example.com", PASSWORD)
        assertNotNull(OrderRepository(helper).recover(userId))
    }
    companion object { private const val DB="shop-coldstart-test.db"; private const val PASSWORD="StrongPassword123" }
}

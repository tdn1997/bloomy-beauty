package com.example.bloomybeauty.feature.cart

import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class CartRefreshTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper:AuthDatabaseHelper
    private lateinit var vm:CartViewModel
    private lateinit var repository:CartRepository
    private var userId=0L
    private val store=ViewModelStore()
    @Before fun setup()=runBlocking {
        context.deleteDatabase(DB);helper=AuthDatabaseHelper(context,DB)
        userId=(AuthRepository(helper).register("Nguyễn Thị Mỹ","refresh@example.com",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        repository=CartRepository(helper);repository.add(userId,"cleanser");repository.setQuantity(userId,"cleanser",2)
        repository.saveShipping(userId,ShippingDetails("Nguyễn Thị Mỹ","0912345678","123 Nguyễn Huệ, Thành phố Hồ Chí Minh"))
        val orders=OrderRepository(helper);orders.prepare(userId)
        compose.runOnUiThread {vm=CartViewModel(repository,userId,"Nguyễn Thị Mỹ",orders);store.put("cart",vm)}
        compose.setContent {val state by vm.state.collectAsState();BloomyBeautyTheme {CartScreen(state,{vm.back()},vm::setQuantity,vm::remove,vm::acceptPrices,vm::continueToShipping,vm::editDraft,vm::saveShipping,vm::reload,vm::placeOrder)}}
        ready();assertEquals(CartStep.REVIEW,vm.state.value.step)
    }
    @After fun cleanup() {compose.runOnUiThread{store.clear()};helper.close();context.deleteDatabase(DB)}
    private fun ready() {compose.waitUntil(10000){!vm.state.value.loading && !vm.state.value.busy};compose.waitForIdle()}
    private fun change(sql:String) {helper.writableDatabase.execSQL(sql);compose.runOnUiThread{vm.reload()};ready()}
    @Test fun priceChangedDuringReviewRequiresDisplayedPriceConfirmation() {
        change("UPDATE products SET price_vnd=200000,revision=revision+1 WHERE id='cleanser'")
        assertEquals(CartStep.ITEMS,vm.state.value.step);assertEquals(CartError.PRICE_CHANGED,vm.state.value.error)
        compose.onNodeWithText("Xác nhận giá mới cho giỏ hàng").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Xác nhận giá mới cho giỏ hàng").performScrollTo().performClick();ready()
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().assertIsEnabled()
        assertTrue(runBlocking{OrderRepository(helper).list(userId)}.isEmpty())
    }
    @Test fun hiddenProductDuringReviewRequiresRemoval() {
        change("UPDATE products SET active=0,revision=revision+1 WHERE id='cleanser'")
        assertEquals(CartStep.ITEMS,vm.state.value.step);assertEquals(CartError.UNAVAILABLE,vm.state.value.error)
        compose.onNodeWithText("Sản phẩm đã ngừng bán. Vui lòng xóa khỏi giỏ.").assertExists()
        compose.onNodeWithTag("remove_cleanser").performScrollTo().performClick();ready()
        assertTrue(vm.state.value.snapshot.items.isEmpty())
    }
    @Test fun lowerStockDuringReviewRequiresReducingQuantity() {
        change("UPDATE products SET stock=1,revision=revision+1 WHERE id='cleanser'")
        assertEquals(CartStep.ITEMS,vm.state.value.step);assertEquals(CartError.STOCK,vm.state.value.error)
        compose.onNodeWithText("Giảm về tồn kho hiện tại").performScrollTo().performClick();ready()
        assertEquals(1,vm.state.value.snapshot.quantity)
        compose.onNodeWithText("Tiếp tục giao hàng").performScrollTo().assertIsEnabled()
    }
    @Test fun resumeDoesNotJumpFromShippingBackToOldReview() {
        compose.runOnUiThread {vm.back();vm.editDraft(vm.state.value.draft.copy(recipient="Trần Thị Mỹ"));vm.reload()};ready()
        assertEquals(CartStep.SHIPPING,vm.state.value.step);assertEquals("Trần Thị Mỹ",vm.state.value.draft.recipient)
        compose.onNodeWithText("Thông tin giao hàng").assertIsDisplayed()
        assertTrue(runBlocking{OrderRepository(helper).list(userId)}.isEmpty())
    }
    companion object {private const val DB="cart-refresh-test.db";private const val PASSWORD="StrongPassword123"}
}

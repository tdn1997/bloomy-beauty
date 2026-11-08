package com.example.bloomybeauty.feature.address

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.address.AddressRepository
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.feature.cart.*
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class AddressScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var addresses: AddressViewModel
    private lateinit var cart: CartViewModel
    private lateinit var cartRepository: CartRepository
    private val store = ViewModelStore()
    private var userId = 0L
    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB);helper = AuthDatabaseHelper(context,DB)
        userId = (AuthRepository(helper).register("Khách địa chỉ","ui@address.test","AddressTest123!","AddressTest123!") as AuthResult.Success).user.id
        cartRepository = CartRepository(helper);cartRepository.add(userId,"cleanser")
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge()
            addresses = AddressViewModel(AddressRepository(helper),userId)
            cart = CartViewModel(cartRepository,userId,"Khách địa chỉ",OrderRepository(helper))
            store.put("addresses",addresses);store.put("cart",cart)
        }
    }
    @After fun cleanup() {compose.runOnUiThread {store.clear()};helper.close();context.deleteDatabase(DB)}
    @Composable private fun Harness(initial: ShippingDetails? = null) {
        var book by rememberSaveable {mutableStateOf(true)}
        val addressState by addresses.state.collectAsState()
        val cartState by cart.state.collectAsState()
        BloomyBeautyTheme {
            if(book) AddressScreen(addressState,initial,{book = false},addresses::reload,addresses::save,
                addresses::setDefault,addresses::delete,onSelect = {token -> addresses.select(token) {details ->
                    cartRepository.saveShipping(userId,details);cart.editDraft(details);cart.reload();book = false
                }})
            else CartScreen(cartState,{cart.back()},cart::setQuantity,cart::remove,cart::acceptPrices,cart::continueToShipping,
                cart::editDraft,cart::saveShipping,cart::reload,cart::placeOrder,onOpenAddresses = {book = true},
                onCouponChange = cart::editCoupon,onApplyCoupon = cart::applyCoupon,onRemoveCoupon = cart::removeCoupon)
        }
    }
    private fun await() {compose.waitUntil(10000) {!addresses.state.value.loading && !addresses.state.value.busy && !cart.state.value.loading && !cart.state.value.busy}}
    private fun capture(name: String) {
        compose.runOnUiThread {compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(compose.activity.window.decorView.windowToken,0)}
        compose.waitForIdle();android.os.SystemClock.sleep(500)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val dir = java.io.File(context.getExternalFilesDir(null),"pixel6-address-qa").apply {mkdirs()}
        java.io.File(dir,"$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    private fun fill(label: String,phone: String,address: String) {
        compose.onNodeWithText("Nhãn địa chỉ").performTextReplacement(label)
        compose.onNodeWithText("Người nhận").performTextReplacement("Khách địa chỉ")
        compose.onNodeWithText("Số điện thoại").performTextReplacement(phone)
        compose.onNodeWithText("Địa chỉ nhận hàng").performTextReplacement(address)
    }
    @Test fun createEditDefaultSelectAndDeleteSourcePreservesCheckoutSnapshot() {
        compose.setContent {Harness()};await()
        compose.runOnUiThread {cart.continueToShipping()};await()
        compose.onNodeWithText("Thêm địa chỉ").performClick()
        fill("Nhà","123",HOME)
        compose.onNodeWithText("Lưu địa chỉ").performScrollTo().performClick();await()
        compose.onNodeWithText("Kiểm tra người nhận",substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Số điện thoại").performScrollTo().performTextReplacement("+84 912 345 678")
        compose.onNodeWithText("Lưu địa chỉ").performScrollTo().performClick();await()
        assertEquals("0912345678",addresses.state.value.addresses.single().shipping.phone)
        capture("01-dia-chi-dau-tien")
        compose.onNodeWithText("Thêm địa chỉ").performScrollTo().performClick()
        fill("Văn phòng","0987654321",OFFICE)
        compose.onNodeWithText("Lưu địa chỉ").performScrollTo().performClick();await()
        compose.onNodeWithText("Đặt mặc định").performScrollTo().performClick();await()
        assertEquals("Văn phòng",addresses.state.value.addresses.first().label)
        compose.onNodeWithText("Địa chỉ mặc định").performScrollTo().assertIsDisplayed()
        capture("02-doi-mac-dinh")
        compose.onAllNodesWithText("Dùng địa chỉ này").onLast().performScrollTo().performClick();await()
        assertEquals(HOME,cart.state.value.draft.address)
        capture("03-chon-tai-checkout")
        compose.onNodeWithText("Chọn từ sổ địa chỉ").performScrollTo().performClick();await()
        compose.onAllNodesWithText("Sửa").onLast().performScrollTo().performClick()
        compose.onNodeWithText("Địa chỉ nhận hàng").performTextReplacement("999 Nguyễn Huệ, Thành phố Hồ Chí Minh")
        compose.onNodeWithText("Lưu địa chỉ").performScrollTo().performClick();await()
        assertEquals(HOME,cart.state.value.draft.address)
        compose.onAllNodesWithText("Xóa").onLast().performScrollTo().performClick()
        compose.onNode(hasText("Xóa") and hasAnyAncestor(isDialog())).performClick();await()
        assertEquals(1,addresses.state.value.addresses.size)
        compose.onNodeWithContentDescription("Quay lại").performClick()
        compose.onNodeWithText(HOME).assertExists()
        compose.onNodeWithText("Lưu và kiểm tra thông tin").performScrollTo().performClick();await()
        assertEquals(HOME,cart.state.value.request!!.shipping.address)
        compose.onNodeWithText("Xác nhận đặt hàng COD").performScrollTo().performClick();await()
        assertEquals(HOME,cart.state.value.placedOrder!!.shipping.address)
        assertEquals("0912345678",cart.state.value.placedOrder!!.shipping.phone)
        capture("04-don-giu-dia-chi-snapshot")
    }
    @Test fun explicitSavePresetAndDraftSurviveStateRestoration() {
        val preset = ShippingDetails("Người nhận bản nháp","+84 912 345 678",HOME)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {Harness(preset)};await()
        compose.onNodeWithText("Nhãn địa chỉ").performTextInput("Địa chỉ trong bản nháp")
        restoration.emulateSavedInstanceStateRestore();await()
        compose.onNodeWithText("Nhãn địa chỉ").assertTextContains("Địa chỉ trong bản nháp")
        compose.onNodeWithText("Người nhận").assertTextContains(preset.recipient)
        compose.onNodeWithText("Lưu địa chỉ").performScrollTo().performClick();await()
        assertEquals("Địa chỉ trong bản nháp",addresses.state.value.addresses.single().label)
        assertEquals("0912345678",addresses.state.value.addresses.single().shipping.phone)
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM shipping_details WHERE user_id=?",arrayOf("$userId")).use {it.moveToFirst();assertEquals(0,it.getInt(0))}
        capture("05-luu-tu-ban-nhap")
    }
    companion object {
        private const val DB = "address-ui-test.db"
        private const val HOME = "123 Nguyễn Huệ, Thành phố Hồ Chí Minh"
        private const val OFFICE = "456 Lê Lợi, Thành phố Hồ Chí Minh"
    }
}

package com.example.bloomybeauty.feature.promotions

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.bloomybeauty.data.catalog.CatalogSeed
import com.example.bloomybeauty.data.promotions.*
import com.example.bloomybeauty.ui.theme.BloomyBeautyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PromotionsSectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val sale = FlashSale("sale", "Giờ vàng", "cleanser", 149_000, 1_000, 2_000)
    private val coupon = CouponCampaign("coupon", "Mã chào mừng", "BLOOMY20", DiscountType.PERCENT,
        20, 200_000, 50_000, 1_000, 2_000)

    @Test fun liveStateChangesUpdateAndExpireVisibleOffers() {
        val state = mutableStateOf(PromotionsUiState(PromotionsFeed(loading = false,
            flashSales = listOf(sale), coupons = listOf(coupon)), 1_000))
        var openedProduct: String? = null
        compose.setContent {
            BloomyBeautyTheme { PromotionsSection(state.value, CatalogSeed.products, {}, { openedProduct = it }) }
        }
        compose.onNodeWithText("FLASH SALE · Giờ vàng").assertIsDisplayed()
        compose.onNodeWithText("BLOOMY20").assertIsDisplayed()
        compose.onNodeWithText("Xem sản phẩm").performClick()
        compose.runOnIdle { assertEquals("cleanser", openedProduct) }
        compose.runOnIdle { state.value = state.value.copy(feed = state.value.feed.copy(
            flashSales = listOf(sale.copy(title = "Giờ vàng mới")))) }
        compose.onNodeWithText("FLASH SALE · Giờ vàng mới").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(now = 2_000) }
        compose.onNodeWithText("FLASH SALE · Giờ vàng mới").assertDoesNotExist()
        compose.onNodeWithText("BLOOMY20").assertDoesNotExist()
        compose.onNodeWithText("Chưa có chương trình ưu đãi đang mở.").assertIsDisplayed()
    }

    @Test fun cachedCodesCannotBeCopiedAndErrorsOfferRetry() {
        val state = mutableStateOf(PromotionsUiState(PromotionsFeed(loading = false,
            coupons = listOf(coupon), fromCache = true), 1_000))
        var retried = false
        compose.setContent {
            BloomyBeautyTheme { PromotionsSection(state.value, CatalogSeed.products, { retried = true }, {}) }
        }
        compose.onNodeWithText("Sao chép BLOOMY20").assertIsNotEnabled()
        compose.onNodeWithText("Đang hiển thị dữ liệu đã lưu. Kết nối mạng để xác nhận ưu đãi.").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(feed = state.value.feed.copy(
            coupons = emptyList(), error = PromotionsError.PERMISSION_DENIED)) }
        compose.onNodeWithText("Tải lại ưu đãi").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }
}

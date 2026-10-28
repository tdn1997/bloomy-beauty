package com.example.bloomybeauty.data.promotions

import com.example.bloomybeauty.feature.promotions.PromotionsUiState
import org.junit.Assert.*
import org.junit.Test

class PromotionRulesTest {
    private fun sale() = mapOf<String, Any?>(
        "published" to true, "title" to "Giờ vàng", "productId" to "cleanser",
        "salePriceVnd" to 149_000L, "startsAt" to 1_000L, "endsAt" to 2_000L)
    private fun coupon() = mapOf<String, Any?>(
        "published" to true, "title" to "Mã chào mừng", "code" to "BLOOMY20",
        "discountType" to "percent", "discountValue" to 20L, "minOrderVnd" to 200_000L,
        "maxDiscountVnd" to 50_000L, "startsAt" to 1_000L, "endsAt" to 2_000L)

    @Test fun flashSaleTransitionsAtExactStartAndEnd() {
        val sale = PromotionDecoder.flashSale("sale", sale())!!
        assertEquals(PromotionPhase.UPCOMING, sale.phase(999))
        assertEquals(PromotionPhase.ACTIVE, sale.phase(1_000))
        assertEquals(PromotionPhase.ACTIVE, sale.phase(1_999))
        assertEquals(PromotionPhase.EXPIRED, sale.phase(2_000))
    }
    @Test fun unpublishedAndIncompleteDocumentsAreIgnored() {
        assertNull(PromotionDecoder.flashSale("x", sale() + ("published" to false)))
        assertNull(PromotionDecoder.flashSale("x", sale() - "productId"))
        assertNull(PromotionDecoder.coupon("x", coupon() - "code"))
        assertNull(PromotionDecoder.coupon("x", coupon() + ("published" to false)))
    }
    @Test fun invalidWindowsAreRejected() {
        for (end in listOf(0L, 999L, 1_000L)) {
            assertNull(PromotionDecoder.flashSale("x", sale() + ("endsAt" to end)))
            assertNull(PromotionDecoder.coupon("x", coupon() + ("endsAt" to end)))
        }
        assertNull(PromotionDecoder.flashSale("x", sale() + ("startsAt" to "2026-01-01")))
        assertNull(PromotionDecoder.coupon("x", coupon() + ("startsAt" to -1L)))
    }
    @Test fun pricesMustBePositiveBoundedIntegers() {
        for (price in listOf(0L, -1L, 1_000_000_001L, 149_000.0, "149000")) {
            assertNull(PromotionDecoder.flashSale("x", sale() + ("salePriceVnd" to price)))
        }
    }
    @Test fun malformedCouponBenefitsAreRejected() {
        for (value in listOf(0L, -1L, 101L, 20.0)) {
            assertNull(PromotionDecoder.coupon("x", coupon() + ("discountValue" to value)))
        }
        assertNull(PromotionDecoder.coupon("x", coupon() - "maxDiscountVnd"))
        assertNull(PromotionDecoder.coupon("x", coupon() + ("minOrderVnd" to -1L)))
        assertNull(PromotionDecoder.coupon("x", coupon() + ("discountType" to "anything")))
    }
    @Test fun fixedDiscountCanOmitPercentageCap() {
        val decoded = PromotionDecoder.coupon("x", (coupon() - "maxDiscountVnd") +
            mapOf("discountType" to "fixed", "discountValue" to 30_000L))!!
        assertEquals(DiscountType.FIXED, decoded.discountType)
        assertEquals(30_000L, decoded.discountValue)
        assertNull(decoded.maxDiscountVnd)
    }
    @Test fun invalidCouponCodesAreRejected() {
        for (code in listOf("", "hello world", "bloomy20", "A".repeat(33))) {
            assertNull(PromotionDecoder.coupon("x", coupon() + ("code" to code)))
        }
    }
    @Test fun expiredSalesAndUnreleasedCodesAreHiddenWithoutAServerChange() {
        val feed = PromotionsFeed(loading = false,
            flashSales = listOf(PromotionDecoder.flashSale("sale", sale())!!),
            coupons = listOf(PromotionDecoder.coupon("coupon", coupon())!!))
        assertEquals(1, PromotionsUiState(feed, 999).flashSales.size)
        assertTrue(PromotionsUiState(feed, 999).coupons.isEmpty())
        assertEquals(1, PromotionsUiState(feed, 1_000).coupons.size)
        assertTrue(PromotionsUiState(feed, 2_000).coupons.isEmpty())
        assertTrue(PromotionsUiState(feed, 2_000).flashSales.isEmpty())
    }
    @Test fun malformedDisplayTextIsRejected() {
        assertNull(PromotionDecoder.flashSale("x", sale() + ("title" to " ")))
        assertNull(PromotionDecoder.coupon("x", coupon() + ("title" to "x".repeat(121))))
    }
}

package com.example.bloomybeauty.data.coupon

import com.example.bloomybeauty.data.cart.ShippingDetails
import com.example.bloomybeauty.data.order.CheckoutLine
import com.example.bloomybeauty.data.order.CheckoutRequest
import org.junit.Assert.*
import org.junit.Test

class CouponPricingTest {
    private val rule = CouponRule("SAVE20",CouponKind.PERCENT,20,0,null,100,200,true,0)
    private val shipping = ShippingDetails("Buyer","0912345678","123 Example Street")
    private fun error(expected: CouponError, block: () -> Unit) {
        try {block();fail("Expected $expected")} catch(e: CouponException) {assertEquals(expected,e.error)}
    }
    @Test fun percentRoundsDownToVndAndHonorsCap() {
        assertEquals(34_170L,rule.copy(value = 17).quote(201_001,100).discountVnd)
        assertEquals(10_000L,rule.copy(value = 17,maximumDiscount = 10_000).quote(201_001,100).discountVnd)
    }
    @Test fun percentDoesNotOverflowForLargeValidSubtotals() {
        assertEquals(8_301_034_833_169_298_226L,rule.copy(value = 90).quote(Long.MAX_VALUE,100).discountVnd)
        assertEquals(Long.MAX_VALUE,rule.copy(value = 100).quote(Long.MAX_VALUE,100).discountVnd)
    }
    @Test fun fixedDiscountClampsToSubtotalAndCap() {
        val fixed = rule.copy(kind = CouponKind.FIXED,value = 50_000)
        assertEquals(1_000L,fixed.quote(1_000,100).discountVnd)
        assertEquals(500L,fixed.copy(maximumDiscount = 500).quote(1_000,100).discountVnd)
        assertEquals(0L,fixed.quote(0,100).discountVnd)
    }
    @Test fun timeWindowIncludesStartAndExcludesEnd() {
        assertEquals(20L,rule.quote(100,100).discountVnd)
        assertEquals(20L,rule.quote(100,199).discountVnd)
        error(CouponError.NOT_STARTED) {rule.quote(100,99)}
        error(CouponError.EXPIRED) {rule.quote(100,200)}
        error(CouponError.INACTIVE) {rule.copy(active = false).quote(100,150)}
    }
    @Test fun minimumIsCheckedBeforePricing() {
        val restricted = rule.copy(minimumSubtotal = 200_000)
        error(CouponError.MINIMUM) {restricted.quote(199_999,150)}
        assertEquals(40_000L,restricted.quote(200_000,150).discountVnd)
    }
    @Test fun invalidRulesAreRejected() {
        error(CouponError.INVALID_RULE) {rule.copy(value = 101).quote(100,150)}
        error(CouponError.INVALID_RULE) {rule.copy(value = 0).quote(100,150)}
        error(CouponError.INVALID_RULE) {rule.copy(maximumDiscount = -1).quote(100,150)}
        error(CouponError.INVALID_RULE) {rule.copy(endsAt = 100).quote(100,150)}
    }
    @Test fun freeShippingThresholdUsesSubtotalBeforeDiscount() {
        val quote = rule.copy(kind = CouponKind.FIXED,value = 50_000).quote(500_000,150)
        val request = CheckoutRequest("id",listOf(CheckoutLine("sku",1,500_000)),shipping,quote)
        assertEquals(0L,request.feeVnd);assertEquals(450_000L,request.totalVnd)
    }
    @Test fun percentAndPaidShippingMatchConfirmedTotal() {
        val request = CheckoutRequest("id",listOf(CheckoutLine("sku",1,200_000)),shipping,rule.quote(200_000,150))
        assertEquals(30_000L,request.feeVnd);assertEquals(190_000L,request.totalVnd)
    }
    @Test fun identityRevisionAndFeeArePartOfFingerprint() {
        val base = CheckoutRequest("id",listOf(CheckoutLine("sku",1,500_000)),shipping,rule.quote(500_000,150))
        assertNotEquals(base.fingerprint,base.copy(coupon = base.coupon!!.copy(rule = rule.copy(revision = 1))).fingerprint)
        assertNotEquals(base.fingerprint,base.copy(coupon = base.coupon!!.copy(rule = rule.copy(code = "OTHER20"))).fingerprint)
        assertNotEquals(base.fingerprint,base.copy(quotedFeeVnd = 30_000).fingerprint)
    }
    @Test fun normalizedCodeDoesNotDependOnUserLocale() {
        val old = java.util.Locale.getDefault()
        try {java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));assertEquals("FIXED20",normalizeCoupon(" fixed20 "))}
        finally {java.util.Locale.setDefault(old)}
    }
    @Test fun legacyFingerprintRemainsCompatibleForCommittedRetries() {
        val legacy = CheckoutRequest("old",listOf(CheckoutLine("sku",2,100)),shipping,version = 1)
        assertEquals("e0c5d2ba0383aaccd9b70606f60c6602809fa809208e520ea640f6e48c80a94f",legacy.fingerprint)
        assertNotEquals(legacy.fingerprint,legacy.copy(version = 2).fingerprint)
    }
    @Test(expected = IllegalArgumentException::class) fun negativeDiscountCannotProduceTotal() {
        CheckoutRequest("id",listOf(CheckoutLine("sku",1,100)),shipping,CouponQuote(rule,-1)).totalVnd
    }
}

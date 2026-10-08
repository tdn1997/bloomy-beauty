package com.example.bloomybeauty.data.order

import com.example.bloomybeauty.data.cart.ShippingDetails
import org.junit.Assert.*
import org.junit.Test

class CheckoutRulesTest {
    private val shipping = ShippingDetails("Nguyễn Thị Mỹ", "0912345678", "12 Nguyễn Văn A, TP Hồ Chí Minh")

    @Test fun deliveryFeeBoundaryAndTotalsUseIntegerVnd() {
        assertEquals(30_000L, DeliveryPricing.fee(499_999))
        assertEquals(0L, DeliveryPricing.fee(500_000))
        val request = CheckoutRequest("test", listOf(CheckoutLine("cleanser", 2, 192_000)), shipping)
        assertEquals(384_000L, request.subtotalVnd)
        assertEquals(414_000L, request.totalVnd)
    }
    @Test fun fingerprintTracksContentAndIgnoresRequestIdAndLineOrdering() {
        val lines = listOf(CheckoutLine("a", 1, 10), CheckoutLine("b", 2, 20))
        val request = CheckoutRequest("first", lines, shipping)
        assertEquals(request.fingerprint, request.copy(id = "retry", lines = lines.reversed()).fingerprint)
        assertNotEquals(request.fingerprint, request.copy(shipping = shipping.copy(address = "Một địa chỉ khác")).fingerprint)
        assertNotEquals(request.fingerprint, request.copy(lines = listOf(lines.first().copy(quantity = 2), lines.last())).fingerprint)
        assertNotEquals(request.fingerprint, request.copy(lines = listOf(lines.first().copy(unitPriceVnd = 11), lines.last())).fingerprint)
    }
    @Test(expected = ArithmeticException::class) fun orderTotalCannotWrapOnOverflow() {
        CheckoutRequest("test", listOf(CheckoutLine("a", 2, Long.MAX_VALUE)), shipping).totalVnd
    }
}

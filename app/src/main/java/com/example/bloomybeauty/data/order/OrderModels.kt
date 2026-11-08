package com.example.bloomybeauty.data.order

import com.example.bloomybeauty.data.cart.ShippingDetails
import com.example.bloomybeauty.data.coupon.CouponQuote
import java.security.MessageDigest

data class CheckoutLine(val productId: String, val quantity: Int, val unitPriceVnd: Long)
data class CheckoutRequest(val id: String, val lines: List<CheckoutLine>, val shipping: ShippingDetails,
    val coupon: CouponQuote? = null, val version: Int = 2, val quotedFeeVnd: Long? = null) {
    val fingerprint: String get() {
        fun field(value: String) = "${value.length}:$value"
        val content = lines.sortedBy { it.productId }.joinToString("") {
            field(it.productId) + field(it.quantity.toString()) + field(it.unitPriceVnd.toString())
        } + field(shipping.recipient) + field(shipping.phone) + field(shipping.address)
        val versioned = if(version == 1) content else field("checkout-v2") + content +
            field(coupon?.rule?.identity().orEmpty()) + field(discountVnd.toString()) + field(subtotalVnd.toString()) + field(feeVnd.toString()) + field(totalVnd.toString())
        return MessageDigest.getInstance("SHA-256").digest(versioned.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    val subtotalVnd: Long get() = lines.fold(0L) { sum, line -> Math.addExact(sum, Math.multiplyExact(line.unitPriceVnd, line.quantity.toLong())) }
    val feeVnd: Long get() = quotedFeeVnd ?: DeliveryPricing.fee(subtotalVnd)
    val discountVnd: Long get() = coupon?.discountVnd ?: 0
    val totalVnd: Long get() {
        require(discountVnd in 0..subtotalVnd && feeVnd >= 0)
        return Math.addExact(Math.subtractExact(subtotalVnd,discountVnd),feeVnd)
    }
}

object DeliveryPricing {
    fun fee(subtotal: Long): Long {
        require(subtotal >= 0)
        return if (subtotal >= 500_000) 0 else 30_000
    }
}

data class OrderLine(val productId: String, val name: String, val volume: String, val imageKey: String, val quantity: Int, val unitPriceVnd: Long) {
    val subtotalVnd: Long get() = Math.multiplyExact(unitPriceVnd, quantity.toLong())
}
data class CustomerOrder(
    val id: Long, val requestId: String, val createdAt: Long, val status: String,
    val shipping: ShippingDetails, val subtotalVnd: Long, val feeVnd: Long, val totalVnd: Long, val lines: List<OrderLine>,
    val discountVnd: Long = 0, val couponCode: String? = null, val couponRuleSnapshot: String? = null,
) {
    val code: String get() = "BB-${id.toString().padStart(6, '0')}"
}
data class CheckoutRecovery(val request: CheckoutRequest, val order: CustomerOrder?)
enum class OrderError { SESSION, REQUEST_CONFLICT, CART_CHANGED, STORAGE, INVALID_REQUEST, NOT_FOUND, CANNOT_CANCEL, STOCK_OVERFLOW, QUOTE_CHANGED }
class OrderException(val error: OrderError) : Exception(error.name)

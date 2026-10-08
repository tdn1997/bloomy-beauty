package com.example.bloomybeauty.data.order

import com.example.bloomybeauty.data.cart.ShippingDetails
import java.security.MessageDigest

data class CheckoutLine(val productId: String, val quantity: Int, val unitPriceVnd: Long)
data class CheckoutRequest(val id: String, val lines: List<CheckoutLine>, val shipping: ShippingDetails) {
    val fingerprint: String get() {
        fun field(value: String) = "${value.length}:$value"
        val content = lines.sortedBy { it.productId }.joinToString("") {
            field(it.productId) + field(it.quantity.toString()) + field(it.unitPriceVnd.toString())
        } + field(shipping.recipient) + field(shipping.phone) + field(shipping.address)
        return MessageDigest.getInstance("SHA-256").digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
    val subtotalVnd: Long get() = lines.fold(0L) { sum, line -> Math.addExact(sum, Math.multiplyExact(line.unitPriceVnd, line.quantity.toLong())) }
    val feeVnd: Long get() = DeliveryPricing.fee(subtotalVnd)
    val totalVnd: Long get() = Math.addExact(subtotalVnd, feeVnd)
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
) {
    val code: String get() = "BB-${id.toString().padStart(6, '0')}"
}
data class CheckoutRecovery(val request: CheckoutRequest, val order: CustomerOrder?)
enum class OrderError { SESSION, REQUEST_CONFLICT, CART_CHANGED, STORAGE, INVALID_REQUEST, NOT_FOUND, CANNOT_CANCEL, STOCK_OVERFLOW }
class OrderException(val error: OrderError) : Exception(error.name)

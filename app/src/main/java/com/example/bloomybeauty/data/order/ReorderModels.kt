package com.example.bloomybeauty.data.order

import com.example.bloomybeauty.data.cart.MAX_CART_QUANTITY

enum class ReorderReason { MISSING, INACTIVE, OUT_OF_STOCK, LIMIT, CART_INVALID, ACCEPT_PRICE, PARTIAL }
enum class ReorderError { SESSION, NOT_FOUND, STATUS, CHECKOUT_PENDING, CHANGED, USED, EMPTY, STORAGE, TOTAL_OVERFLOW }
class ReorderException(val error: ReorderError) : Exception(error.name)

data class ReorderLine(
    val productId: String,
    val name: String,
    val requested: Int,
    val added: Int,
    val existing: Int,
    val oldPriceVnd: Long,
    val currentPriceVnd: Long?,
    val reason: ReorderReason? = null,
) {
    val subtotalVnd: Long get() = Math.multiplyExact(currentPriceVnd ?: 0, added.toLong())
}

// Deliberately not saved in a Bundle: process recreation requires a fresh preview.
class ReorderPreview internal constructor(
    val userId: Long,
    val orderId: Long,
    val lines: List<ReorderLine>,
    internal val source: ReorderSource,
) {
    internal var consumed = false
    val addedQuantity: Int get() = lines.sumOf { it.added }
    val subtotalVnd: Long get() = lines.fold(0L) { total, line -> Math.addExact(total, line.subtotalVnd) }
    val needsPriceAcceptance: Boolean get() = lines.any { it.reason == ReorderReason.ACCEPT_PRICE }
    val canConfirm: Boolean get() = addedQuantity > 0 && !needsPriceAcceptance
}

internal data class ReorderProduct(val name: String, val price: Long, val stock: Int, val active: Boolean, val revision: Long)
internal data class ReorderCartLine(val productId: String, val quantity: Int, val price: Long)
internal data class ReorderSource(
    val order: CustomerOrder,
    val cart: List<ReorderCartLine>,
    val products: Map<String, ReorderProduct?>,
    val checkout: List<String>,
)

internal object ReorderRules {
    fun line(old: OrderLine, product: ReorderProduct?, existing: ReorderCartLine?): ReorderLine {
        val quantity = existing?.quantity ?: 0
        val limit = minOf(product?.stock ?: 0, MAX_CART_QUANTITY)
        val reason = when {
            product == null -> ReorderReason.MISSING
            !product.active -> ReorderReason.INACTIVE
            product.stock == 0 -> ReorderReason.OUT_OF_STOCK
            quantity > limit -> ReorderReason.CART_INVALID
            existing != null && existing.price != product.price -> ReorderReason.ACCEPT_PRICE
            quantity == limit -> ReorderReason.LIMIT
            else -> null
        }
        val added = if (reason == null) minOf(old.quantity, (limit - quantity).coerceAtLeast(0)) else 0
        return ReorderLine(old.productId, product?.name ?: old.name, old.quantity, added, quantity, old.unitPriceVnd, product?.price,
            reason ?: if (added < old.quantity) ReorderReason.PARTIAL else null)
    }
}

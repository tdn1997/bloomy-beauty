package com.example.bloomybeauty.data.cart

import com.example.bloomybeauty.data.catalog.Product

const val MAX_CART_QUANTITY = 99

data class CartItem(val product: Product, val quantity: Int, val acceptedPriceVnd: Long) {
    val priceChanged: Boolean get() = acceptedPriceVnd != product.priceVnd
    val available: Boolean get() = product.active && quantity in 1..minOf(MAX_CART_QUANTITY, product.stock)
    val subtotalVnd: Long get() = Math.multiplyExact(product.priceVnd, quantity.toLong())
}

data class ShippingDetails(val recipient: String = "", val phone: String = "", val address: String = "")
enum class ShippingError { NAME, PHONE, ADDRESS }

object ShippingValidation {
    // The demo accepts Vietnamese mobile numbers in domestic or +84/84 form.
    fun normalize(details: ShippingDetails): ShippingDetails {
        val phone = details.phone.trim().replace(Regex("[\\s.()-]"), "")
        val domestic = when {
            phone.startsWith("+84") -> "0" + phone.drop(3)
            phone.startsWith("84") -> "0" + phone.drop(2)
            else -> phone
        }
        return details.copy(recipient = details.recipient.trim(), phone = domestic, address = details.address.trim())
    }

    fun validate(details: ShippingDetails): ShippingError? {
        val normalized = normalize(details)
        return when {
            normalized.recipient.length !in 2..80 -> ShippingError.NAME
            !normalized.phone.matches(Regex("0[35789][0-9]{8}")) -> ShippingError.PHONE
            normalized.address.length !in 10..300 -> ShippingError.ADDRESS
            else -> null
        }
    }
}

data class CartSnapshot(val items: List<CartItem> = emptyList(), val shipping: ShippingDetails = ShippingDetails()) {
    val quantity: Int get() = items.sumOf { it.quantity }
    val totalVnd: Long get() = items.fold(0L) { total, item -> Math.addExact(total, item.subtotalVnd) }
    val canContinue: Boolean get() = items.isNotEmpty() && items.all { it.available && !it.priceChanged }
}

enum class CartError { UNAVAILABLE, STOCK, QUANTITY, PRICE_CHANGED, EMPTY, STORAGE, TOTAL_OVERFLOW, SESSION }
class CartException(val error: CartError) : Exception(error.name)
class ShippingException(val error: ShippingError) : Exception(error.name)

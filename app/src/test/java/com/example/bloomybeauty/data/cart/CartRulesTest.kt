package com.example.bloomybeauty.data.cart

import com.example.bloomybeauty.data.catalog.CatalogSeed
import org.junit.Assert.*
import org.junit.Test

class CartRulesTest {
    @Test
    fun totalsUseCurrentIntegerPricesAndQuantities() {
        val first = CatalogSeed.products.first().copy(priceVnd = 192_000)
        val second = CatalogSeed.products.last().copy(priceVnd = 32_000)
        val snapshot = CartSnapshot(listOf(CartItem(first, 2, 192_000), CartItem(second, 3, 32_000)))
        assertEquals(5, snapshot.quantity)
        assertEquals(480_000L, snapshot.totalVnd)
        assertTrue(snapshot.canContinue)
    }

    @Test
    fun emptyChangedHiddenAndInsufficientItemsBlockShipping() {
        val product = CatalogSeed.products.first()
        assertFalse(CartSnapshot().canContinue)
        for (item in listOf(
            CartItem(product, 1, product.priceVnd - 1),
            CartItem(product.copy(active = false), 1, product.priceVnd),
            CartItem(product.copy(stock = 0), 1, product.priceVnd),
            CartItem(product.copy(stock = 2), 3, product.priceVnd),
        )) assertFalse(CartSnapshot(listOf(item)).canContinue)
    }

    @Test(expected = ArithmeticException::class)
    fun overflowDoesNotBecomeNegativeMoney() {
        CartSnapshot(listOf(CartItem(CatalogSeed.products.first().copy(priceVnd = Long.MAX_VALUE), 2, Long.MAX_VALUE))).totalVnd
    }

    @Test
    fun phoneAndWhitespaceNormalizeOnlyWhenValidatingOrSaving() {
        val details = ShippingDetails("  Nguyễn Thị Mỹ  ", "+84 912 345 678", "  12 Nguyễn Văn A, Phường B, TP Hồ Chí Minh  ")
        assertNull(ShippingValidation.validate(details))
        val normalized = ShippingValidation.normalize(details)
        assertEquals("Nguyễn Thị Mỹ", normalized.recipient)
        assertEquals("0912345678", normalized.phone)
        assertEquals("+84 912 345 678", details.phone)
        assertEquals("0912345678", ShippingValidation.normalize(details.copy(phone = "84912345678")).phone)
    }

    @Test
    fun shippingRejectsEmptyFieldsInvalidPhonesAndOversizedAddress() {
        val valid = ShippingDetails("Nguyễn Mỹ", "0912345678", "12 Nguyễn Văn A, TP Hồ Chí Minh")
        assertEquals(ShippingError.NAME, ShippingValidation.validate(valid.copy(recipient = " ")))
        for (phone in listOf("", "0123456789", "09123456789", "091234567a", "+850912345678")) {
            assertEquals(ShippingError.PHONE, ShippingValidation.validate(valid.copy(phone = phone)))
        }
        assertEquals(ShippingError.ADDRESS, ShippingValidation.validate(valid.copy(address = "ngắn")))
        assertEquals(ShippingError.ADDRESS, ShippingValidation.validate(valid.copy(address = "a".repeat(301))))
    }
}

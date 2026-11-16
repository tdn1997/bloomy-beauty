package com.example.bloomybeauty.data.order

import org.junit.Assert.*
import org.junit.Test

class ReorderRulesTest {
    private val old = OrderLine("cleanser", "Tên trong đơn cũ", "140 ml", "cleanser", 3, 192000)
    private val product = ReorderProduct("Tên hiện tại", 200000, 30, true, 1)

    @Test fun emptyCartUsesCurrentNameAndPriceAndKeepsHistoricalPriceForComparison() {
        val line = ReorderRules.line(old, product, null)
        assertEquals("Tên hiện tại", line.name)
        assertEquals(192000L, line.oldPriceVnd); assertEquals(200000L, line.currentPriceVnd)
        assertEquals(3, line.added); assertEquals(600000L, line.subtotalVnd)
        assertNull(line.reason)
    }
    @Test fun sameProductMergesWithExistingQuantity() {
        val line = ReorderRules.line(old, product, ReorderCartLine("cleanser", 2, 200000))
        assertEquals(2, line.existing); assertEquals(3, line.added); assertNull(line.reason)
    }
    @Test fun shortageAddsOnlyTheRemainingUnit() {
        val line = ReorderRules.line(old, product.copy(stock = 2), ReorderCartLine("cleanser", 1, 200000))
        assertEquals(1, line.added); assertEquals(ReorderReason.PARTIAL, line.reason)
    }
    @Test fun quantityCapAppliesEvenWithLargeStock() {
        val line = ReorderRules.line(old, product.copy(stock = 1000), ReorderCartLine("cleanser", 98, 200000))
        assertEquals(1, line.added); assertEquals(ReorderReason.PARTIAL, line.reason)
        assertEquals(ReorderReason.LIMIT, ReorderRules.line(old, product.copy(stock = 1000), ReorderCartLine("cleanser", 99, 200000)).reason)
    }
    @Test fun missingInactiveAndEmptyStockNeverAdd() {
        for ((current, reason) in listOf(null to ReorderReason.MISSING, product.copy(active = false) to ReorderReason.INACTIVE,
            product.copy(stock = 0) to ReorderReason.OUT_OF_STOCK)) {
            val line = ReorderRules.line(old, current, null)
            assertEquals(reason, line.reason); assertEquals(0, line.added)
            if (current == null) { assertEquals(old.name, line.name); assertNull(line.currentPriceVnd) }
        }
    }
    @Test fun invalidExistingCartIsNotSilentlyReduced() {
        val line = ReorderRules.line(old, product.copy(stock = 1), ReorderCartLine("cleanser", 2, 200000))
        assertEquals(2, line.existing); assertEquals(0, line.added); assertEquals(ReorderReason.CART_INVALID, line.reason)
    }
    @Test fun existingUnacceptedPriceBlocksAdditionEvenWhenPriceDecreases() {
        for (accepted in listOf(180000L, 220000L)) {
            val line = ReorderRules.line(old, product, ReorderCartLine("cleanser", 1, accepted))
            assertEquals(0, line.added); assertEquals(ReorderReason.ACCEPT_PRICE, line.reason)
        }
    }
    @Test fun allValidQuantityCombinationsPreserveExistingAndNeverExceedStockOr99() {
        for (stock in listOf(0, 1, 2, 30, 98, 99, 100, Int.MAX_VALUE)) {
            for (existing in 0..99) for (requested in listOf(1, 3, 99)) {
                val line = ReorderRules.line(old.copy(quantity = requested), product.copy(stock = stock),
                    if (existing == 0) null else ReorderCartLine("cleanser", existing, 200000))
                assertEquals(existing, line.existing)
                assertTrue(line.added in 0..requested)
                if (existing <= minOf(stock, 99)) assertTrue(existing + line.added <= minOf(stock, 99))
                else assertEquals(0, line.added)
            }
        }
    }
    @Test(expected = ArithmeticException::class) fun subtotalRejectsOverflowInsteadOfWrapping() {
        ReorderRules.line(old, product.copy(price = Long.MAX_VALUE), null).subtotalVnd
    }
}

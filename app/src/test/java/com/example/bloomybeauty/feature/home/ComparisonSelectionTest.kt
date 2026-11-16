package com.example.bloomybeauty.feature.home

import com.example.bloomybeauty.data.catalog.CatalogSeed
import org.junit.Assert.*
import org.junit.Test

class ComparisonSelectionTest {
    private val products = CatalogSeed.products
    @Test fun selectionAddsRemovesAndRejectsFourthProductWithoutReplacingOthers() {
        var selected = emptyList<String>()
        for (p in products.take(4)) selected = ComparisonSelection.toggle(selected, p.id, products)
        assertEquals(products.take(3).map { it.id }, selected)
        selected = ComparisonSelection.toggle(selected, products.first().id, products)
        selected = ComparisonSelection.toggle(selected, products[3].id, products)
        assertEquals(listOf(products[1].id, products[2].id, products[3].id), selected)
    }
    @Test fun reconcileDeduplicatesPreservesOrderAndDropsMissingInactiveProducts() {
        val catalog = products.map { if (it.id == "serum") it.copy(active = false) else it }
        assertEquals(listOf("toner", "cleanser"), ComparisonSelection.reconcile(listOf("toner", "toner", "missing", "serum", "cleanser"), catalog))
        assertTrue(ComparisonSelection.toggle(emptyList(), "missing", catalog).isEmpty())
        assertTrue(ComparisonSelection.toggle(emptyList(), "serum", catalog).isEmpty())
    }
    @Test fun outOfStockProductCanStillBeCompared() {
        val catalog = products.map { it.copy(stock = 0) }
        assertEquals(listOf("cleanser"), ComparisonSelection.toggle(emptyList(), "cleanser", catalog))
    }
    @Test fun reconcileIsIdempotentAndNeverExceedsThree() {
        val input = products.map { it.id } + products.map { it.id }
        val selected = ComparisonSelection.reconcile(input, products)
        assertEquals(3, selected.size)
        assertEquals(selected, ComparisonSelection.reconcile(selected, products))
        assertTrue(ComparisonSelection.reconcile(selected, emptyList()).isEmpty())
    }
    @Test fun missingInformationIsExplicitWithoutInventingValues() {
        assertEquals("Chưa có thông tin", ComparisonSelection.information("  "))
        assertEquals("140 ml", ComparisonSelection.information("140 ml"))
    }
}

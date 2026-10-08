package com.example.bloomybeauty.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

class ProductSearchTest {
    @Test
    fun vietnameseNamesMatchWithOrWithoutAccents() {
        val accented = search("Cà phê Đắk Lắk")
        assertEquals(accented, search("ca phe dak lak"))
        assertEquals(accented, search(Normalizer.normalize("Cà phê Đắk Lắk", Normalizer.Form.NFD)))
        assertTrue(accented.isNotEmpty())
    }

    @Test
    fun queryMatchesBrandAndMultipleWords() {
        assertEquals(listOf("micellar"), search("cocoon tay trang").map { it.id })
        assertTrue(search("khong co san pham").isEmpty())
    }

    @Test
    fun filtersCombineCategoryAndFavorites() {
        val result = ProductSearch.filter(CatalogSeed.products, "", "skin", ProductSort.FEATURED,
            favoritesOnly = true, favoriteIds = setOf("cleanser", "lip_balm"))
        assertEquals(listOf("cleanser"), result.map { it.id })
    }

    @Test
    fun sortingUsesIntegerPriceAndDoesNotChangeSourceList() {
        val original = CatalogSeed.products.toList()
        val ascending = ProductSearch.filter(original, "", null, ProductSort.PRICE_ASCENDING)
        val descending = ProductSearch.filter(original, "", null, ProductSort.PRICE_DESCENDING)
        assertEquals("lip_balm", ascending.first().id)
        assertEquals(ascending.map { it.priceVnd }.reversed(), descending.map { it.priceVnd })
        assertEquals(original, CatalogSeed.products)
    }

    private fun search(query: String) = ProductSearch.filter(CatalogSeed.products, query, null, ProductSort.FEATURED)
}

package com.example.bloomybeauty.data.catalog

import java.text.Normalizer
import java.util.Locale

data class Product(
    val id: String,
    val name: String,
    val categoryId: String,
    val volume: String,
    val priceVnd: Long,
    val imageKey: String,
    val description: String,
    val ingredients: String,
    val usage: String,
    val sourceUrl: String,
    val brand: String = "Cocoon",
    val stock: Int = 30,
    val active: Boolean = true,
    val revision: Long = 0,
)

data class Category(val id: String, val name: String)
data class CatalogSnapshot(val categories: List<Category>, val products: List<Product>, val favoriteIds: Set<String>)
enum class ProductSort { FEATURED, PRICE_ASCENDING, PRICE_DESCENDING }

object ProductSearch {
    private val combiningMarks = Regex("\\p{M}+")

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(combiningMarks, "").lowercase(Locale.ROOT).replace('đ', 'd').trim()

    fun filter(
        products: List<Product>, query: String, categoryId: String?, sort: ProductSort,
        favoritesOnly: Boolean = false, favoriteIds: Set<String> = emptySet(),
    ): List<Product> {
        val words = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        val matches = products.filter { product ->
            product.active && (categoryId == null || categoryId == product.categoryId) &&
                (!favoritesOnly || product.id in favoriteIds) &&
                words.all { it in normalize("${product.name} ${product.brand} ${product.volume}") }
        }
        return when (sort) {
            ProductSort.FEATURED -> matches
            ProductSort.PRICE_ASCENDING -> matches.sortedBy { it.priceVnd }
            ProductSort.PRICE_DESCENDING -> matches.sortedByDescending { it.priceVnd }
        }
    }
}

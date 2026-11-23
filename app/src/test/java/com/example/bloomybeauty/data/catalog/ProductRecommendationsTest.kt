package com.example.bloomybeauty.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductRecommendationsTest {
    private val anchor = CatalogSeed.products.first().copy(
        name = "Gel bí đao",
        description = "",
        ingredients = "",
    )
    private val sameCategory = anchor.copy(id = "serum", name = "Tinh chất hoa hồng")
    private val similar = anchor.copy(id = "similar", categoryId = "body", name = "Gel bi dao")
    private val unrelated = anchor.copy(id = "unrelated", categoryId = "hair", name = "Dầu gội hương bưởi")

    @Test
    fun sameCategoryAndVietnameseTextRankWhileUnavailableAndUnrelatedProductsAreExcluded() {
        val products = listOf(
            unrelated, similar, anchor, sameCategory,
            similar.copy(id = "sold-out", stock = 0),
            similar.copy(id = "inactive", active = false),
        )
        val results = ProductRecommendations.forDetail(products, anchor)

        assertEquals(listOf("serum", "similar"), results.map { it.product.id })
        assertEquals(listOf(RecommendationReason.SAME_CATEGORY, RecommendationReason.SIMILAR_CONTENT), results.map { it.reason })
    }

    @Test
    fun homeUsesTheBestFavoriteAndDoesNotRecommendAlreadySavedProducts() {
        val otherFavorite = sameCategory.copy(id = "other-favorite")
        val products = listOf(anchor, otherFavorite, sameCategory, similar)

        val results = ProductRecommendations.forHome(products, setOf(anchor.id, otherFavorite.id))

        assertEquals(listOf(sameCategory.id, similar.id), results.map { it.product.id })
        assertEquals(listOf(otherFavorite.name, anchor.name), results.map { it.favoriteName })
        assertTrue(results.all { it.reason == RecommendationReason.FAVORITE })
    }

    @Test
    fun homeFallsBackToAvailableProductsWithoutClaimingAPersonalizedMatch() {
        val products = listOf(anchor.copy(stock = 0), unrelated, similar.copy(active = false))
        for (favorites in listOf(emptySet(), setOf(anchor.id), setOf(similar.id, "missing"))) {
            val results = ProductRecommendations.forHome(products, favorites)
            assertEquals(listOf(ProductRecommendation(unrelated, RecommendationReason.DISCOVER)), results)
        }
        assertTrue(ProductRecommendations.forHome(products, setOf(unrelated.id)).isEmpty())
    }

    @Test
    fun homeAndDetailLimitTheNumberOfResults() {
        val products = listOf(anchor) + List(8) { sameCategory.copy(id = "candidate-$it") }

        assertEquals(4, ProductRecommendations.forHome(products, setOf(anchor.id)).size)
        assertEquals(3, ProductRecommendations.forDetail(products, anchor).size)
    }
}

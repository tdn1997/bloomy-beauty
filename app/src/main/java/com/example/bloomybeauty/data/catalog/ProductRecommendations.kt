package com.example.bloomybeauty.data.catalog

import org.apache.commons.text.similarity.CosineSimilarity

enum class RecommendationReason { DISCOVER, FAVORITE, SAME_CATEGORY, SIMILAR_CONTENT }

data class ProductRecommendation(
    val product: Product,
    val reason: RecommendationReason,
    val favoriteName: String = "",
)

object ProductRecommendations {
    private const val MIN_CONTENT_SIMILARITY = 0.2
    private val cosine = CosineSimilarity()
    private val token = Regex("[a-z0-9]+")

    fun forHome(products: List<Product>, favoriteIds: Set<String>): List<ProductRecommendation> {
        val favorites = products.filter { it.active && it.id in favoriteIds }
        val candidates = products.filter { it.canBuy() && it.id !in favoriteIds }
        val matches = rank(candidates, favorites).take(4)
        if (matches.isEmpty()) {
            return candidates.take(4).map { ProductRecommendation(it, RecommendationReason.DISCOVER) }
        }
        return matches.map {
            ProductRecommendation(it.product, RecommendationReason.FAVORITE, it.anchor.name)
        }
    }

    fun forDetail(products: List<Product>, selected: Product): List<ProductRecommendation> {
        if (!selected.active) return emptyList()
        val candidates = products.filter { it.canBuy() && it.id != selected.id }
        return rank(candidates, listOf(selected)).take(3).map {
            val reason = if (it.sameCategory) RecommendationReason.SAME_CATEGORY else RecommendationReason.SIMILAR_CONTENT
            ProductRecommendation(it.product, reason)
        }
    }

    private fun rank(candidates: List<Product>, anchors: List<Product>): List<Match> {
        if (anchors.isEmpty()) return emptyList()
        val anchorVectors = anchors.map { it to phrases(it) }
        return candidates.mapNotNull { candidate ->
            val vector = phrases(candidate)
            anchorVectors.mapNotNull { (anchor, anchorVector) ->
                val sameCategory = candidate.categoryId == anchor.categoryId
                val similarity = cosine.cosineSimilarity(vector, anchorVector)
                if (sameCategory || similarity >= MIN_CONTENT_SIMILARITY) {
                    Match(candidate, anchor, sameCategory, similarity)
                } else null
            }.maxByOrNull { it.score }
        }.sortedByDescending { it.score }
    }

    // Phrases of two tokens reduce false matches on single syllables in Vietnamese.
    private fun phrases(product: Product): Map<CharSequence, Int> = buildMap {
        for (field in listOf(product.name, product.description, product.ingredients)) {
            token.findAll(ProductSearch.normalize(field)).map { it.value }
                .zipWithNext { first, second -> "$first $second" }
                .forEach { put(it, 1) }
        }
    }

    private fun Product.canBuy() = active && stock > 0

    private data class Match(
        val product: Product,
        val anchor: Product,
        val sameCategory: Boolean,
        val similarity: Double,
    ) {
        val score: Double get() = similarity + if (sameCategory) 1.0 else 0.0
    }
}

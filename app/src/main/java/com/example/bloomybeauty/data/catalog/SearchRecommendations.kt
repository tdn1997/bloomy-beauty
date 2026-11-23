package com.example.bloomybeauty.data.catalog

enum class SearchGoal(vararg val phrases: String) {
    FACE_CLEANSING("lam sach da mat", "lam sach mat"),
    FACE_WASH("rua mat"),
    MAKEUP_REMOVAL("tay trang", "nuoc tay trang"),
    LIP_MOISTURE("duong moi", "son duong", "son duong moi"),
    LIP_CARE("cham soc moi"),
    HAIR_CARE("cham soc toc", "lam sach toc", "dau goi", "goi dau"),
}

enum class SearchReason(val category: String, val namePhrase: String) {
    FACE_WASH("skin", "rua mat"),
    MAKEUP_REMOVER("skin", "tay trang"),
    LIP_BALM("lips", "son duong"),
    LIP_EXFOLIANT("lips", "da chet moi"),
    SHAMPOO("hair", "dau goi"),
}

data class SearchCandidate(val productId: String, val reason: SearchReason)

fun interface SearchAiRanker {
    suspend fun rank(goal: SearchGoal, candidates: List<SearchCandidate>): List<SearchCandidate>
}

internal object SearchRecommendations {
    const val MAX_RESULTS = 4

    fun goal(query: String): SearchGoal? {
        val normalized = ProductSearch.normalize(query).replace(Regex("\\s+"), " ")
        return SearchGoal.entries.firstOrNull { normalized in it.phrases }
    }

    fun candidates(goal: SearchGoal, products: List<Product>): List<SearchCandidate> {
        val reasons = when (goal) {
            SearchGoal.FACE_CLEANSING -> listOf(SearchReason.FACE_WASH, SearchReason.MAKEUP_REMOVER)
            SearchGoal.FACE_WASH -> listOf(SearchReason.FACE_WASH)
            SearchGoal.MAKEUP_REMOVAL -> listOf(SearchReason.MAKEUP_REMOVER)
            SearchGoal.LIP_MOISTURE -> listOf(SearchReason.LIP_BALM)
            SearchGoal.LIP_CARE -> listOf(SearchReason.LIP_BALM, SearchReason.LIP_EXFOLIANT)
            SearchGoal.HAIR_CARE -> listOf(SearchReason.SHAMPOO)
        }
        return products.asSequence()
            .filter { it.active && it.stock > 0 }
            .mapNotNull { product ->
                val name = " ${ProductSearch.normalize(product.name)} "
                reasons.firstOrNull { it.category == product.categoryId && " ${it.namePhrase} " in name }
                    ?.let { SearchCandidate(product.id, it) }
            }
            .take(12)
            .toList()
    }

    fun validate(
        suggestions: List<SearchCandidate>,
        candidates: List<SearchCandidate>,
    ): List<SearchCandidate> = suggestions
        .filter { it in candidates }
        .distinctBy { it.productId }
        .take(MAX_RESULTS)
}

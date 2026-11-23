package com.example.bloomybeauty.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchRecommendationsTest {
    @Test
    fun onlyRecognizedShoppingGoalsAreMappedFromTheQuery() {
        assertEquals(SearchGoal.FACE_CLEANSING, SearchRecommendations.goal("  LÀM  sạch da mặt  "))
        assertEquals(SearchGoal.LIP_MOISTURE, SearchRecommendations.goal("duong moi"))
        assertNull(SearchRecommendations.goal("không dưỡng môi"))
        assertNull(SearchRecommendations.goal("dưỡng môi cho thuan@example.com"))
    }

    @Test
    fun candidateUsesCurrentNameCategoryAndAvailability() {
        val catalog = CatalogSeed.products
        val goal = SearchGoal.FACE_CLEANSING
        val cleanser = SearchCandidate("cleanser", SearchReason.FACE_WASH)
        val micellar = SearchCandidate("micellar", SearchReason.MAKEUP_REMOVER)
        assertEquals(listOf(cleanser, micellar), SearchRecommendations.candidates(goal, catalog))
        assertEquals(listOf(micellar), SearchRecommendations.candidates(SearchGoal.MAKEUP_REMOVAL, catalog))
        assertEquals(emptyList<SearchCandidate>(), SearchRecommendations.candidates(goal, catalog.map {
            when (it.id) {
                "cleanser" -> it.copy(stock = 0)
                "micellar" -> it.copy(name = "Gel dưỡng ẩm")
                else -> it
            }
        }))
    }

    @Test
    fun aiCanOnlyReorderKnownCandidatesWithVerifiedReasons() {
        val cleanser = SearchCandidate("cleanser", SearchReason.FACE_WASH)
        val micellar = SearchCandidate("micellar", SearchReason.MAKEUP_REMOVER)
        assertEquals(listOf(micellar, cleanser), SearchRecommendations.validate(
            listOf(
                cleanser.copy(reason = SearchReason.SHAMPOO),
                SearchCandidate("invented", SearchReason.FACE_WASH),
                micellar, micellar, cleanser,
            ),
            listOf(cleanser, micellar),
        ))
    }
}

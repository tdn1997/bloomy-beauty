package com.example.bloomybeauty.feature.promotions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloomybeauty.data.promotions.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

/** Clock ticks update schedule boundaries even when Firestore has no document changes. */
data class PromotionsUiState(val feed: PromotionsFeed = PromotionsFeed(enabled = false, loading = false), val now: Long = 0L) {
    val flashSales: List<FlashSale> get() = feed.flashSales.filter { it.phase(now) != PromotionPhase.EXPIRED }
    // Codes are shown only during their release window.
    val coupons: List<CouponCampaign> get() = feed.coupons.filter { it.phase(now) == PromotionPhase.ACTIVE }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PromotionsViewModel(repository: PromotionsRepository) : ViewModel() {
    private val refresh = MutableStateFlow(0L)
    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }
    val state = combine(refresh.flatMapLatest { repository.observe() }, clock, ::PromotionsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), PromotionsUiState())

    fun retry() { refresh.value += 1 }

    class Factory(private val repository: PromotionsRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PromotionsViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return PromotionsViewModel(repository) as T
        }
    }
}

package com.example.bloomybeauty.feature.home

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.catalog.Category
import com.example.bloomybeauty.data.catalog.Product
import com.example.bloomybeauty.data.catalog.ProductSort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

data class HomeUiState(
    val loading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val savingFavorite: Boolean = false,
    val error: Boolean = false,
    val query: String = "",
    val categoryId: String? = null,
    val sort: ProductSort = ProductSort.FEATURED,
)

class HomeViewModel(private val repository: CatalogRepository, private val userId: Long) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())
    val state = mutableState.asStateFlow()

    private var loadJob: Job? = null

    init { reload() }

    fun reload() {
        loadJob?.cancel()
        mutableState.update { it.copy(loading = true, error = false) }
        loadJob = viewModelScope.launch {
            try {
                val snapshot = repository.load(userId)
                mutableState.update {
                    it.copy(loading = false, categories = snapshot.categories, products = snapshot.products,
                        favoriteIds = snapshot.favoriteIds, error = false)
                }
            } catch (exception: com.example.bloomybeauty.data.auth.SessionException) {
                mutableState.update { it.copy(loading=false,savingFavorite=false,error=true,products=emptyList(),favoriteIds=emptySet()) }
            } catch (exception: SQLiteException) {
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    fun setQuery(query: String) { mutableState.update { it.copy(query = query) } }
    fun setCategory(id: String?) { mutableState.update { it.copy(categoryId = id) } }
    fun setSort(sort: ProductSort) { mutableState.update { it.copy(sort = sort) } }

    fun toggleFavorite(productId: String) {
        if (state.value.savingFavorite || state.value.loading) return
        mutableState.update { it.copy(savingFavorite = true, error = false) }
        viewModelScope.launch {
            try {
                repository.toggleFavorite(userId, productId)
                val snapshot = repository.load(userId)
                mutableState.update { it.copy(savingFavorite = false, favoriteIds = snapshot.favoriteIds) }
            } catch (exception: com.example.bloomybeauty.data.auth.SessionException) {
                mutableState.update { it.copy(loading=false,savingFavorite=false,error=true,products=emptyList(),favoriteIds=emptySet()) }
            } catch (exception: SQLiteException) {
                mutableState.update { it.copy(savingFavorite = false, error = true) }
            }
        }
    }

    class Factory(private val repository: CatalogRepository, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository, userId) as T
        }
    }
}

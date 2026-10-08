package com.example.bloomybeauty.feature.auth

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloomybeauty.data.auth.AuthError
import com.example.bloomybeauty.data.auth.AuthRepository
import com.example.bloomybeauty.data.auth.AuthResult
import com.example.bloomybeauty.data.auth.AuthUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val initializing: Boolean = true,
    val busy: Boolean = false,
    val registering: Boolean = false,
    val user: AuthUser? = null,
    val error: AuthError? = null,
)

class AuthViewModel(private val repository: AuthRepository) : ViewModel(), ViewModelStoreOwner {
    // Retained across configuration changes, cleared only after a successful logout.
    override val viewModelStore = ViewModelStore()

    override fun onCleared() {
        viewModelStore.clear()
    }

    private val mutableState = MutableStateFlow(AuthUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val user = repository.restoreSession()
                mutableState.update { it.copy(user = user, initializing = false) }
            } catch (exception: SQLiteException) {
                mutableState.update { it.copy(initializing = false, error = AuthError.STORAGE) }
            }
        }
    }

    fun switchMode() {
        if (state.value.busy || state.value.initializing) return
        mutableState.update { it.copy(registering = !it.registering, error = null) }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    fun onProfileUpdated(user: AuthUser) {
        mutableState.update { if (it.user?.id == user.id) it.copy(user = user) else it }
    }

    fun submit(name: String, email: String, password: String, confirmation: String) {
        if (state.value.busy || state.value.initializing || state.value.user != null) return
        val registering = state.value.registering
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = if (registering) repository.register(name, email, password, confirmation)
                    else repository.login(email, password)
                mutableState.update {
                    when (result) {
                        is AuthResult.Success -> it.copy(busy = false, user = result.user, error = null)
                        is AuthResult.Failure -> it.copy(busy = false, error = result.error)
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: SQLiteException) {
                mutableState.update { it.copy(busy = false, error = AuthError.STORAGE) }
            }
        }
    }

    fun logout() {
        if (state.value.busy || state.value.user == null) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                repository.logout()
                viewModelStore.clear()
                mutableState.value = AuthUiState(initializing = false)
            } catch (exception: SQLiteException) {
                mutableState.update { it.copy(busy = false, error = AuthError.STORAGE) }
            }
        }
    }

    class Factory(private val repository: AuthRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AuthViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(repository) as T
        }
    }
}

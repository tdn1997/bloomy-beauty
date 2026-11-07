package com.example.bloomybeauty.feature.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloomybeauty.data.auth.SessionException
import com.example.bloomybeauty.data.care.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CareUiState(val loading: Boolean = true, val busy: Boolean = false, val routines: List<CareRoutine> = emptyList(), val error: String? = null)

class CareRoutineViewModel(private val repository: CareRoutineRepository, private val userId: Long) : ViewModel() {
    private val mutableState = MutableStateFlow(CareUiState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    init { reload() }

    fun reload() {
        observation?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        observation = viewModelScope.launch {
            try { repository.observe(userId).collect { rows -> mutableState.update { it.copy(loading = false, routines = rows) } } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(loading = false, routines = emptyList(), error = message(e)) } }
        }
    }

    fun save(id: Long?, revision: Long?, draft: CareRoutineDraft, done: () -> Unit) = action {
        repository.save(userId, id, revision, draft); done()
    }
    fun toggle(routine: CareRoutine, enabled: Boolean) = action { repository.setEnabled(userId, routine, enabled) }
    fun delete(routine: CareRoutine, done: () -> Unit) = action { repository.delete(userId, routine); done() }
    fun clearError() { mutableState.update { it.copy(error = null) } }

    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(error = message(e)) } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }

    private fun message(e: Exception): String = when (e) {
        is SessionException -> "Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại."
        is IllegalArgumentException, is IllegalStateException -> e.message ?: "Dữ liệu lịch chưa hợp lệ."
        else -> "Không thể đọc hoặc lưu lịch. Vui lòng thử lại."
    }

    class Factory(private val repository: CareRoutineRepository, private val userId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CareRoutineViewModel::class.java))
            return CareRoutineViewModel(repository, userId) as T
        }
    }
}

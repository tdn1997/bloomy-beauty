package com.example.bloomybeauty.feature.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloomybeauty.data.auth.AuthError
import com.example.bloomybeauty.data.auth.AuthValidation
import com.example.bloomybeauty.data.cloud.*
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CloudAccountState(val initializing: Boolean=true,val busy: Boolean=false,val registering: Boolean=false,
    val session: CloudSession?=null,val account: CloudAccount?=null,val authError: AuthError?=null,val notice: String?=null)
class CloudAccountViewModel(private val auth: CloudAuthGateway,private val accounts: CloudAccounts): ViewModel() {
    private val mutableState=MutableStateFlow(CloudAccountState())
    val state=mutableState.asStateFlow()
    private var load: Job?=null
    private var operation: Job?=null
    private var unsubscribe: (() -> Unit)?=null
    private var pendingReload=false
    init {viewModelScope.launch {
        auth.session.collect {session ->
            load?.cancel();pendingReload=false;unsubscribe?.invoke();unsubscribe=null
            mutableState.value=CloudAccountState(initializing=false,busy=operation?.isActive==true,registering=state.value.registering,session=session)
            if(session!=null) {
                unsubscribe=accounts.observeRole(session) {refresh()}
                refresh()
            }
        }
    }}
    fun switchMode() {if(!state.value.busy) mutableState.value=state.value.copy(registering=!state.value.registering,authError=null,notice=null)}
    fun clearError() {mutableState.value=state.value.copy(authError=null,notice=null)}
    fun submit(name: String,email: String,password: String,confirmation: String) {
        if(state.value.busy || auth.session.value!=null) return
        val registering=state.value.registering
        val validation=if(registering) AuthValidation.validate(email,password,name,confirmation)
            else when {
                !AuthValidation.validEmail(email) -> AuthError.INVALID_EMAIL
                password.isBlank() || password.length>4096 -> AuthError.INVALID_CREDENTIALS
                else -> null
            }
        validation?.let {
            mutableState.value=state.value.copy(authError=it);return
        }
        mutableState.value=state.value.copy(busy=true,authError=null,notice=null)
        operation=viewModelScope.launch {
            try {
                if(registering) auth.register(name.trim(),AuthValidation.normalizeEmail(email),password)
                else auth.login(AuthValidation.normalizeEmail(email),password)
            } catch(e: CancellationException) {throw e}
            catch(_: FirebaseAuthUserCollisionException) {mutableState.value=state.value.copy(authError=AuthError.DUPLICATE_EMAIL)}
            catch(_: FirebaseAuthWeakPasswordException) {mutableState.value=state.value.copy(authError=AuthError.INVALID_PASSWORD)}
            catch(_: FirebaseAuthInvalidCredentialsException) {mutableState.value=state.value.copy(authError=AuthError.INVALID_CREDENTIALS)}
            catch(_: FirebaseAuthInvalidUserException) {mutableState.value=state.value.copy(authError=AuthError.INVALID_CREDENTIALS)}
            catch(_: CloudProfileIncomplete) {mutableState.value=state.value.copy(notice="Tài khoản đã được tạo. Hãy đăng nhập để tiếp tục cập nhật hồ sơ.")}
            catch(_: Exception) {mutableState.value=state.value.copy(notice="Không thể kết nối Firebase. Kiểm tra mạng và thử lại.")}
            finally {mutableState.value=state.value.copy(busy=load?.isActive==true)}
        }
    }
    fun refresh() {
        val expected=auth.session.value ?: return
        if(load?.isActive==true) {pendingReload=true;return}
        mutableState.value=state.value.copy(busy=true,notice=null)
        load=viewModelScope.launch {
            try {
                val account=accounts.current(expected)
                auth.requireSession(expected)
                mutableState.value=state.value.copy(account=account)
            } catch(e: CancellationException) {throw e}
            catch(_: CloudSessionChanged) {if(state.value.session==expected) mutableState.value=state.value.copy(account=null)}
            catch(_: Exception) {
                if(auth.session.value==expected) mutableState.value=state.value.copy(account=null,notice="Chưa thể xác minh tài khoản và quyền từ máy chủ. Vui lòng thử lại.")
            } finally {
                if(auth.session.value==expected) {
                    mutableState.value=state.value.copy(busy=operation?.isActive==true)
                    if(pendingReload) {pendingReload=false;load=null;refresh()}
                }
            }
        }
    }
    fun logout() {
        if(operation?.isActive==true) return
        load?.cancel();pendingReload=false
        unsubscribe?.invoke();unsubscribe=null
        mutableState.value=state.value.copy(account=null,busy=true,registering=false,notice=null)
        operation=viewModelScope.launch {
            try {auth.logout()} finally {mutableState.value=state.value.copy(busy=false)}
        }
    }
    override fun onCleared() {unsubscribe?.invoke()}
    class Factory(private val auth: CloudAuthGateway,private val accounts: CloudAccounts): ViewModelProvider.Factory {
        override fun <T: ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CloudAccountViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return CloudAccountViewModel(auth,accounts) as T
        }
    }
}

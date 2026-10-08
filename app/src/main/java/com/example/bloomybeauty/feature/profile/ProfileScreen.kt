package com.example.bloomybeauty.feature.profile

import android.database.sqlite.SQLiteException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloomybeauty.data.auth.AuthUser
import com.example.bloomybeauty.data.cart.ShippingDetails
import com.example.bloomybeauty.data.profile.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileDraft(val name: String = "", val defaultAddress: Boolean = false, val recipient: String = "", val phone: String = "", val address: String = "")
data class ProfileUiState(val loading: Boolean = true, val busy: Boolean = false, val email: String = "", val draft: ProfileDraft = ProfileDraft(), val error: ProfileError? = null)
class ProfileViewModel(private val repository: ProfileRepository, private val userId: Long) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileUiState())
    val state = mutableState.asStateFlow()
    private var action: Job? = null
    init { reload() }
    fun reload() {
        if (action?.isActive == true) return
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        action = viewModelScope.launch {
            try {
                val snapshot = repository.load(userId)
                mutableState.value = ProfileUiState(loading = false, email = snapshot.user.email, draft = ProfileDraft(
                    snapshot.user.name, snapshot.shipping != null, snapshot.shipping?.recipient ?: snapshot.user.name,
                    snapshot.shipping?.phone ?: "", snapshot.shipping?.address ?: ""))
            } catch (exception: ProfileException) { mutableState.value = mutableState.value.copy(loading = false, error = exception.error) }
            catch (exception: SQLiteException) { mutableState.value = mutableState.value.copy(loading = false, error = ProfileError.STORAGE) }
        }
    }
    fun edit(draft: ProfileDraft) { mutableState.value = mutableState.value.copy(draft = draft, error = null) }
    fun save(onSaved: (AuthUser) -> Unit) {
        if (action?.isActive == true) return
        val draft = state.value.draft
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        action = viewModelScope.launch {
            try {
                val snapshot = repository.save(userId, draft.name,
                    if (draft.defaultAddress) ShippingDetails(draft.recipient, draft.phone, draft.address) else null)
                onSaved(snapshot.user)
            } catch (exception: ProfileException) { mutableState.value = mutableState.value.copy(error = exception.error) }
            catch (exception: SQLiteException) { mutableState.value = mutableState.value.copy(error = ProfileError.STORAGE) }
            finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
    class Factory(private val repository: ProfileRepository, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return ProfileViewModel(repository, userId) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(state: ProfileUiState, onBack: () -> Unit, onEdit: (ProfileDraft) -> Unit, onSave: () -> Unit, onRetry: () -> Unit) {
    val focus = LocalFocusManager.current
    BackHandler { if (!state.busy) onBack() }
    Scaffold(topBar = { TopAppBar(title = { Text("Chỉnh sửa hồ sơ") }, navigationIcon = {
        IconButton(onBack, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Quay lại") }
    }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.loading) CircularProgressIndicator()
            else {
                Text(state.email, style = MaterialTheme.typography.bodyLarge)
                ProfileField("Họ tên tài khoản", state.draft.name, { onEdit(state.draft.copy(name = it)) }, !state.busy, state.error == ProfileError.NAME)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Lưu địa chỉ mặc định", Modifier.weight(1f))
                    Switch(state.draft.defaultAddress, { onEdit(state.draft.copy(defaultAddress = it)) }, enabled = !state.busy,
                        modifier = Modifier.semantics { contentDescription = "Lưu địa chỉ mặc định" })
                }
                if (state.draft.defaultAddress) {
                    ProfileField("Người nhận mặc định", state.draft.recipient, { onEdit(state.draft.copy(recipient = it)) }, !state.busy, state.error == ProfileError.RECIPIENT)
                    ProfileField("Số điện thoại mặc định", state.draft.phone, { onEdit(state.draft.copy(phone = it)) }, !state.busy, state.error == ProfileError.PHONE,
                        KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
                    ProfileField("Địa chỉ mặc định", state.draft.address, { onEdit(state.draft.copy(address = it)) }, !state.busy, state.error == ProfileError.ADDRESS, multiline = true)
                    Text("Gồm số nhà, đường, phường/xã và tỉnh/thành phố. Số điện thoại hỗ trợ đầu +84.", style = MaterialTheme.typography.bodySmall)
                } else Text("Lưu hồ sơ khi tắt tùy chọn này sẽ xóa địa chỉ mặc định đã lưu. Bạn vẫn có thể nhập địa chỉ lúc đặt hàng.", style = MaterialTheme.typography.bodySmall)
                Text("Thay đổi hồ sơ không thay thông tin của các đơn đã đặt.", style = MaterialTheme.typography.bodySmall)
                state.error?.let { error -> Text(when (error) {
                    ProfileError.NAME -> "Họ tên cần từ 2 đến 80 ký tự."
                    ProfileError.RECIPIENT -> "Người nhận cần từ 2 đến 80 ký tự."
                    ProfileError.PHONE -> "Vui lòng nhập số di động Việt Nam hợp lệ."
                    ProfileError.ADDRESS -> "Địa chỉ cần từ 10 đến 300 ký tự."
                    ProfileError.SESSION -> "Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại."
                    ProfileError.STORAGE -> "Không thể đọc hoặc lưu hồ sơ. Vui lòng thử lại."
                }, color = MaterialTheme.colorScheme.error) }
                if (state.error == ProfileError.STORAGE) TextButton(onRetry, enabled = !state.busy) { Text("Tải lại hồ sơ") }
                Button({ focus.clearFocus(); onSave() }, enabled = !state.busy && state.email.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Đang lưu…" else "Lưu hồ sơ")
                }
            }
        }
    }
}

@Composable
private fun ProfileField(label: String, text: String, onEdit: (String) -> Unit, enabled: Boolean, error: Boolean,
                         options: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next), multiline: Boolean = false) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(text)) }
    LaunchedEffect(text) { if (value.text != text) value = TextFieldValue(text) }
    OutlinedTextField(value, { value = it; onEdit(it.text) }, label = { Text(label) }, enabled = enabled, isError = error,
        keyboardOptions = options, singleLine = !multiline, minLines = if (multiline) 3 else 1, modifier = Modifier.fillMaxWidth())
}

package com.example.bloomybeauty.feature.address

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloomybeauty.data.address.*
import com.example.bloomybeauty.data.auth.SessionException
import com.example.bloomybeauty.data.cart.ShippingDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddressUiState(val loading: Boolean = true, val busy: Boolean = false,
    val addresses: List<SavedAddress> = emptyList(), val error: String? = null)
class AddressViewModel(private val repository: AddressRepository, private val userId: Long) : ViewModel() {
    private val mutableState = MutableStateFlow(AddressUiState())
    val state = mutableState.asStateFlow()
    init { reload() }
    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true,error = null)
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.value = state.value.copy(error = when(e) {
                is AddressException -> e.message
                is SessionException -> "Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại."
                else -> "Không thể đọc hoặc lưu sổ địa chỉ. Vui lòng thử lại."
            }) }
            finally { mutableState.value = state.value.copy(busy = false,loading = false) }
        }
    }
    fun reload() = action { mutableState.value = state.value.copy(addresses = repository.list(userId)) }
    fun save(label: String, shipping: ShippingDetails, default: Boolean, token: AddressToken?, onSaved: () -> Unit) = action {
        mutableState.value = state.value.copy(addresses = repository.save(userId,label,shipping,default,token)); onSaved()
    }
    fun setDefault(token: AddressToken) = action { mutableState.value = state.value.copy(addresses = repository.setDefault(userId,token)) }
    fun delete(token: AddressToken, onDeleted: () -> Unit) = action {
        mutableState.value = state.value.copy(addresses = repository.delete(userId,token)); onDeleted()
    }
    fun select(token: AddressToken, onSelected: suspend (ShippingDetails) -> Unit) = action { onSelected(repository.select(userId,token)) }
    class Factory(private val repository: AddressRepository, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AddressViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return AddressViewModel(repository,userId) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressScreen(state: AddressUiState, initialShipping: ShippingDetails?, onBack: () -> Unit, onRetry: () -> Unit,
    onSave: (String,ShippingDetails,Boolean,AddressToken?,()->Unit) -> Unit,
    onDefault: (AddressToken) -> Unit, onDelete: (AddressToken,()->Unit) -> Unit,
    onSelect: ((AddressToken) -> Unit)? = null) {
    var editing by rememberSaveable { mutableStateOf(initialShipping != null) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var label by rememberSaveable { mutableStateOf("") }
    var recipient by rememberSaveable { mutableStateOf(initialShipping?.recipient.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(initialShipping?.phone.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(initialShipping?.address.orEmpty()) }
    var default by rememberSaveable { mutableStateOf(false) }
    var originalDefault by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleteRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    val focus = LocalFocusManager.current
    val back = { if (editing && initialShipping == null) editing = false else onBack() }
    BackHandler { if (!state.busy) back() }
    fun edit(item: SavedAddress?) {
        editId = item?.id; editRevision = item?.revision; label = item?.label.orEmpty()
        recipient = item?.shipping?.recipient.orEmpty(); phone = item?.shipping?.phone.orEmpty(); address = item?.shipping?.address.orEmpty()
        default = item?.isDefault ?: state.addresses.isEmpty(); originalDefault = item?.isDefault == true
        editing = true
    }
    if (deleteId != null) AlertDialog(onDismissRequest = {if(!state.busy) deleteId = null},
        title = {Text("Xóa địa chỉ?")}, text = {Column {
            Text("Đơn đã đặt và bản nháp giao hàng đã chọn vẫn giữ thông tin của chúng.")
            state.error?.let {Text(it,color = MaterialTheme.colorScheme.error)}
        }}, confirmButton = {TextButton({onDelete(AddressToken(checkNotNull(deleteId),checkNotNull(deleteRevision))) {deleteId = null}}, enabled = !state.busy) {Text("Xóa")}},
        dismissButton = {TextButton({deleteId = null}, enabled = !state.busy) {Text("Giữ lại")}})
    Scaffold(topBar = {TopAppBar(title = {Text(if(editing) if(editId == null) "Thêm địa chỉ" else "Sửa địa chỉ" else "Sổ địa chỉ")},
        navigationIcon = {IconButton(back, enabled = !state.busy) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Quay lại")}})}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if(state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if(editing) {
                AddressField("Nhãn địa chỉ",label,{label = it},!state.busy)
                AddressField("Người nhận",recipient,{recipient = it},!state.busy)
                AddressField("Số điện thoại",phone,{phone = it},!state.busy,KeyboardType.Phone)
                AddressField("Địa chỉ nhận hàng",address,{address = it},!state.busy,multiline = true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Checkbox(default || state.addresses.isEmpty(),{default = it}, enabled = !state.busy && !originalDefault && state.addresses.isNotEmpty())
                    Text("Đặt làm địa chỉ mặc định")
                }
                Text("Đổi địa chỉ trong sổ không thay bản nháp giao hàng hay đơn đã đặt.",style = MaterialTheme.typography.bodySmall)
                state.error?.let {Text(it,color = MaterialTheme.colorScheme.error)}
                Button({focus.clearFocus();onSave(label,ShippingDetails(recipient,phone,address),default,
                    editId?.let {AddressToken(it,checkNotNull(editRevision))}) {editing = false}}, enabled = !state.busy && !state.loading, modifier = Modifier.fillMaxWidth()) {Text("Lưu địa chỉ")}
                TextButton({if(!state.busy) {editing = false;onRetry()}}, enabled = !state.busy) {Text("Về danh sách và tải lại")}
            } else {
                Text("${state.addresses.size}/10 địa chỉ",style = MaterialTheme.typography.titleMedium)
                Button({edit(null)}, enabled = !state.busy && !state.loading && state.addresses.size < 10, modifier = Modifier.fillMaxWidth()) {Text("Thêm địa chỉ")}
                state.error?.let {Text(it,color = MaterialTheme.colorScheme.error)}
                TextButton(onRetry, enabled = !state.busy) {Text("Tải lại sổ địa chỉ")}
                if(!state.loading && state.addresses.isEmpty()) Text("Bạn chưa lưu địa chỉ nào.")
                state.addresses.forEach { item -> key(item.id) {
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.label,style = MaterialTheme.typography.titleMedium)
                            if(item.isDefault) Text("Địa chỉ mặc định",color = MaterialTheme.colorScheme.primary)
                            Text(item.shipping.recipient);Text(item.shipping.phone);Text(item.shipping.address)
                            if(onSelect != null) Button({onSelect(item.token)}, enabled = !state.busy) {Text("Dùng địa chỉ này")}
                            Row {
                                TextButton({edit(item)}, enabled = !state.busy) {Text("Sửa")}
                                TextButton({deleteId = item.id;deleteRevision = item.revision}, enabled = !state.busy) {Text("Xóa")}
                            }
                            if(!item.isDefault) TextButton({onDefault(item.token)}, enabled = !state.busy) {Text("Đặt mặc định")}
                        }
                    }
                } }
            }
        }
    }
}

@Composable
private fun AddressField(label: String, text: String, onChange: (String) -> Unit, enabled: Boolean,
    type: KeyboardType = KeyboardType.Text, multiline: Boolean = false) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {mutableStateOf(TextFieldValue(text))}
    LaunchedEffect(text) {if(value.text != text) value = TextFieldValue(text)}
    OutlinedTextField(value,{value = it;onChange(it.text)}, label = {Text(label)}, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = type), singleLine = !multiline,
        minLines = if(multiline) 3 else 1, maxLines = if(multiline) 5 else 1, modifier = Modifier.fillMaxWidth())
}

package com.example.bloomybeauty.feature.notification

import android.database.sqlite.SQLiteException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloomybeauty.data.auth.SessionException
import com.example.bloomybeauty.data.notification.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class NotificationUiState(val items: List<InboxItem> = emptyList(),val unread: Long = 0,
    val next: InboxCursor? = null,val busy: Boolean = false,val error: String? = null)
class NotificationViewModel(private val repository: NotificationRepository, private val userId: Long): ViewModel() {
    private val mutableState = MutableStateFlow(NotificationUiState())
    val state = mutableState.asStateFlow()
    private var pendingReload = false
    init {reload()}
    private fun action(block: suspend () -> Unit) {
        mutableState.value = state.value.copy(busy=true,error=null)
        viewModelScope.launch {
            try {block()}
            catch(e: CancellationException) {throw e}
            catch(_: SessionException) {mutableState.value = NotificationUiState(error="Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại.",busy=true)}
            catch(_: NotificationUnavailable) {mutableState.value = state.value.copy(error="Đơn không còn tồn tại hoặc không thuộc tài khoản này.")}
            catch(_: SQLiteException) {mutableState.value = state.value.copy(error="Không thể tải hộp thông báo. Vui lòng thử lại.")}
            finally {mutableState.value=state.value.copy(busy=false);if(pendingReload) {pendingReload=false;reload()}}
        }
    }
    private suspend fun load() {val page=repository.page(userId);mutableState.value=state.value.copy(items=page.items,unread=page.unread,next=page.next)}
    fun reload() {if(state.value.busy) {pendingReload=true;return};action {load()}}
    fun more() {
        if(state.value.busy) return
        val cursor = state.value.next ?: return
        action {val page=repository.page(userId,cursor);mutableState.value=state.value.copy(items=(state.value.items+page.items).distinctBy {it.id},unread=page.unread,next=page.next)}
    }
    fun readAll() {if(!state.value.busy) action {repository.readAll(userId);load()}}
    fun open(id: Long,onOpened: (Long)->Unit) {
        if(state.value.busy) return
        action {val order=repository.open(userId,id);load();onOpened(order.id)}
    }
    class Factory(private val repository: NotificationRepository,private val userId: Long): ViewModelProvider.Factory {
        override fun <T: ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NotificationViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return NotificationViewModel(repository,userId) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(state: NotificationUiState,onBack: ()->Unit,onReload: ()->Unit,onMore: ()->Unit,onReadAll: ()->Unit,onOpen: (Long)->Unit) {
    BackHandler {onBack()}
    Scaffold(topBar={TopAppBar(title={Text("Thông báo (${state.unread})")},navigationIcon={IconButton(onBack) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Quay lại")}},
        actions={TextButton(onReload,enabled=!state.busy) {Text("Tải lại")}})}) {padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("inbox-list"),contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {Text("Lịch sử cập nhật đơn hàng vẫn được lưu khi bạn tắt thông báo hệ thống.")}
            item {OutlinedButton(onReadAll,enabled=!state.busy && state.unread>0) {Text("Đánh dấu tất cả đã đọc")}}
            if(state.busy) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.error?.let {item {Text(it,color=MaterialTheme.colorScheme.error)}}
            if(!state.busy && state.items.isEmpty() && state.error==null) item {Text("Chưa có thông báo.")}
            items(state.items,key={it.id}) {notification ->
                OutlinedCard({onOpen(notification.id)},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(notification.title,style=MaterialTheme.typography.titleMedium)
                        Text(if(notification.readAt==null) "Chưa đọc" else "Đã đọc",color=MaterialTheme.colorScheme.primary)
                        Text(notification.body)
                        Text(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(Instant.ofEpochMilli(notification.createdAt)),style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if(state.next!=null) item {OutlinedButton(onMore,enabled=!state.busy) {Text("Xem thêm")}}
        }
    }
}

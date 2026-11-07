package com.example.bloomybeauty.feature.care

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.bloomybeauty.data.care.*
import com.example.bloomybeauty.data.catalog.Product
import com.example.bloomybeauty.reminders.CareReminderNotifications
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareScreen(state: CareUiState, products: List<Product>, initialProductId: String?, requestedRoutineId: Long?,
               onRequestHandled: () -> Unit, onBack: () -> Unit, onReload: () -> Unit,
               onSave: (Long?, Long?, CareRoutineDraft, () -> Unit) -> Unit,
               onToggle: (CareRoutine, Boolean) -> Unit, onDelete: (CareRoutine, () -> Unit) -> Unit,
               onClearError: () -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val notifications = remember(context) { CareReminderNotifications(context) }
    var notificationsAllowed by remember { mutableStateOf(notifications.allowed()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed = notifications.allowed() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) notificationsAllowed = notifications.allowed() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun enableNotifications() {
        val preferences = context.getSharedPreferences("push_settings", 0)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean("permission_requested", false)) {
            preferences.edit().putBoolean("permission_requested", true).apply()
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!notifications.allowed()) {
            context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, CareReminderNotifications.CHANNEL)
            })
        }
    }

    var editing by rememberSaveable { mutableStateOf(initialProductId != null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var revision by rememberSaveable { mutableStateOf<Long?>(null) }
    var title by rememberSaveable { mutableStateOf(initialProductId?.let { id -> products.find { it.id == id }?.let { "Dùng ${it.name}" } }.orEmpty()) }
    var note by rememberSaveable { mutableStateOf("") }
    var productId by rememberSaveable { mutableStateOf(initialProductId) }
    var minute by rememberSaveable { mutableIntStateOf(21 * 60) }
    var days by rememberSaveable { mutableIntStateOf(127) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var requestError by rememberSaveable { mutableStateOf<String?>(null) }
    fun edit(routine: CareRoutine?) {
        editingId = routine?.id; revision = routine?.revision
        title = routine?.title.orEmpty(); note = routine?.note.orEmpty(); productId = routine?.productId
        minute = routine?.minuteOfDay ?: 21 * 60; days = routine?.daysMask ?: 127; enabled = routine?.enabled ?: true
        editing = true; onClearError()
    }
    LaunchedEffect(requestedRoutineId, state.loading) {
        if (requestedRoutineId != null && !state.loading) {
            val routine = state.routines.find { it.id == requestedRoutineId }
            if (routine != null) edit(routine) else requestError = "Lịch không còn tồn tại hoặc không thuộc tài khoản hiện tại."
            onRequestHandled()
        }
    }
    BackHandler { if (editing && !state.busy) { editing = false; onClearError() } else if (!state.busy) onBack() }
    val back = { if (!state.busy) { if (editing) { editing = false; onClearError() } else onBack() } }
    Scaffold(topBar = { TopAppBar(title = { Text(if (editing) if (editingId == null) "Thêm lịch chăm sóc" else "Sửa lịch chăm sóc" else "Lịch chăm sóc da") },
        navigationIcon = { IconButton(back, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Quay lại") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nhắc theo khoảng giờ bạn chọn. Thông báo có thể đến trễ khi thiết bị tiết kiệm pin.", style = MaterialTheme.typography.bodySmall)
            if (!notificationsAllowed) {
                Text("Thông báo chăm sóc đang bị chặn. Bạn vẫn có thể lưu lịch.")
                TextButton({ enableNotifications() }) { Text("Bật thông báo chăm sóc") }
            }
            (state.error ?: requestError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.loading) CircularProgressIndicator()
            if (editing) {
                OutlinedTextField(title, { title = it }, label = { Text("Tên lịch") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("Ghi chú (tùy chọn)") }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                var choosingProduct by remember { mutableStateOf(false) }
                OutlinedButton({ choosingProduct = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(products.find { it.id == productId }?.name ?: if (productId != null) "Sản phẩm đã ngừng bán" else "Chọn sản phẩm (tùy chọn)")
                }
                if (choosingProduct) AlertDialog(onDismissRequest = { choosingProduct = false }, title = { Text("Chọn sản phẩm") }, text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        TextButton({ productId = null; choosingProduct = false }) { Text("Không liên kết sản phẩm") }
                        products.forEach { product -> TextButton({ productId = product.id; choosingProduct = false }) { Text(product.name) } }
                    }
                }, confirmButton = { TextButton({ choosingProduct = false }) { Text("Đóng") } })
                OutlinedButton({ TimePickerDialog(context, { _, hour, min -> minute = hour * 60 + min }, minute / 60, minute % 60, true).show() }, enabled = !state.busy) {
                    Text("Giờ nhắc: ${careTime(minute)}")
                }
                Text("Ngày lặp")
                listOf(listOf(0, 1, 2, 3), listOf(4, 5, 6)).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEach { day -> FilterChip(days and (1 shl day) != 0,
                            { days = days xor (1 shl day) }, { Text(dayNames[day]) }, enabled = !state.busy) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Bật nhắc", Modifier.weight(1f))
                    Switch(enabled, { enabled = it }, enabled = !state.busy)
                }
                val draft = CareRoutineDraft(title, note, productId, minute, days, enabled)
                if (days == 0) Text("Chọn ít nhất một ngày.", color = MaterialTheme.colorScheme.error)
                Button({ focus.clearFocus(); onSave(editingId, revision, draft) {
                    editing = false
                    if (enabled && !notifications.allowed()) enableNotifications()
                } }, enabled = !state.busy && CareRoutineRules.valid(draft), modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Đang lưu…" else "Lưu lịch")
                }
            } else {
                Button({ edit(null); requestError = null }, enabled = !state.busy && !state.loading, modifier = Modifier.fillMaxWidth()) { Text("Thêm lịch chăm sóc") }
                if (!state.loading && state.routines.isEmpty()) Text("Bạn chưa có lịch chăm sóc. Hãy thêm lịch đầu tiên.")
                state.routines.forEach { routine ->
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(routine.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Switch(routine.enabled, { checked -> onToggle(routine, checked); if (checked && !notifications.allowed()) enableNotifications() }, enabled = !state.busy)
                            }
                            Text("${careTime(routine.minuteOfDay)} · ${careDays(routine.daysMask)}")
                            routine.productId?.let { id -> Text(products.find { it.id == id }?.name ?: "Sản phẩm đã ngừng bán", style = MaterialTheme.typography.bodySmall) }
                            if (routine.note.isNotBlank()) Text(routine.note)
                            Row {
                                TextButton({ edit(routine) }, enabled = !state.busy) { Text("Sửa lịch") }
                                TextButton({ deleteId = routine.id }, enabled = !state.busy) { Text("Xóa lịch") }
                            }
                        }
                    }
                }
                if (state.error != null) TextButton(onReload, enabled = !state.busy) { Text("Tải lại lịch") }
            }
        }
    }
    val deleting = state.routines.find { it.id == deleteId }
    if (deleting != null) AlertDialog(onDismissRequest = { if (!state.busy) deleteId = null }, title = { Text("Xóa lịch chăm sóc?") },
        text = { Text("Lịch “${deleting.title}” và nhắc hẹn tương ứng sẽ được xóa.") },
        confirmButton = { TextButton({ onDelete(deleting) { deleteId = null } }, enabled = !state.busy) { Text("Xóa") } },
        dismissButton = { TextButton({ deleteId = null }, enabled = !state.busy) { Text("Giữ lại") } })
}

private val dayNames = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
private fun careTime(minute: Int) = String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
private fun careDays(mask: Int) = if (mask == 127) "Hằng ngày" else dayNames.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.joinToString(", ")

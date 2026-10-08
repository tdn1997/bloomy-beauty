package com.example.bloomybeauty.feature.order

import android.database.sqlite.SQLiteException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.feature.home.formatPrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OrdersUiState(val loading: Boolean = true, val orders: List<CustomerOrder> = emptyList(), val error: Boolean = false,
                         val busy: Boolean = false, val cancelError: OrderError? = null, val cancelled: Boolean = false)
class OrdersViewModel(private val repository: OrderRepository, private val userId: Long) : ViewModel() {
    private val mutableState = MutableStateFlow(OrdersUiState())
    val state = mutableState.asStateFlow()
    private var action: Job? = null
    init { reload() }
    fun reload() {
        if (action?.isActive == true) return
        mutableState.value = mutableState.value.copy(loading = true, error = false, cancelError = null)
        action = viewModelScope.launch {
            try { mutableState.value = OrdersUiState(loading = false, orders = repository.list(userId)) }
            catch (exception: OrderException) { mutableState.value = mutableState.value.copy(loading=false,orders=emptyList(),cancelError=exception.error) }
            catch (exception: SQLiteException) { mutableState.value = mutableState.value.copy(loading = false, error = true) }
        }
    }
    fun cancel(orderId: Long, onChanged: () -> Unit = {}) {
        if (action?.isActive == true) return
        mutableState.value = mutableState.value.copy(busy = true, cancelError = null, cancelled = false)
        action = viewModelScope.launch {
            try {
                val order = repository.cancel(userId, orderId)
                mutableState.value = mutableState.value.copy(orders = mutableState.value.orders.map { if (it.id == orderId) order else it }, cancelled = true)
                onChanged()
            } catch (exception: OrderException) {
                mutableState.value = mutableState.value.copy(cancelError = exception.error)
                try { mutableState.value = mutableState.value.copy(orders = repository.list(userId)) }
                catch (failure: OrderException) { mutableState.value = mutableState.value.copy(orders=emptyList(),cancelError=failure.error) }
                catch (failure: SQLiteException) { mutableState.value = mutableState.value.copy(error = true) }
            } catch (exception: SQLiteException) {
                mutableState.value = mutableState.value.copy(cancelError = OrderError.STORAGE)
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }
    class Factory(private val repository: OrderRepository, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(OrdersViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return OrdersViewModel(repository, userId) as T
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(state: OrdersUiState, onBack: () -> Unit, onRetry: () -> Unit, onCancel: (Long) -> Unit = {}) {
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val order = state.orders.firstOrNull { it.id == selected }
    val back = { if (selected != null) selected = null else onBack() }
    BackHandler { if (!state.busy) back() }
    if (confirmCancel && order != null) AlertDialog(
        onDismissRequest = { if (!state.busy) confirmCancel = false },
        title = { Text("Hủy đơn ${order.code}?") },
        text = { Text("Bạn muốn hủy đơn này? Chỉ đơn đang chờ xác nhận mới có thể hủy.") },
        confirmButton = { TextButton({ confirmCancel = false; onCancel(order.id) }, enabled = !state.busy) { Text("Xác nhận hủy") } },
        dismissButton = { TextButton({ confirmCancel = false }) { Text("Giữ đơn") } },
    )
    Scaffold(topBar = { TopAppBar(title = { Text(if (order == null) "Đơn hàng của tôi" else order.code) },
        navigationIcon = { IconButton(back, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Quay lại") } },
        actions = { TextButton(onRetry, enabled = !state.busy && !state.loading) { Text("Tải lại") } }) }) { padding ->
        if (order != null) {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OrderSummary(order)
                OrderContents(order)
                state.cancelError?.let { Text(cancelErrorText(it), color = MaterialTheme.colorScheme.error) }
                if (state.error) Text("Không thể tải đơn hàng. Vui lòng thử lại.", color = MaterialTheme.colorScheme.error)
                if (order.status == "PENDING") OutlinedButton({ confirmCancel = true }, enabled = !state.busy && !state.loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Đang hủy…" else "Hủy đơn")
                }
                if (order.status == "CANCELLED") Text("Đơn đã hủy. Bạn có thể chọn sản phẩm để tạo đơn mới.")
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.loading) item { CircularProgressIndicator() }
            state.cancelError?.let { error -> item { Text(orderErrorText(error),color=MaterialTheme.colorScheme.error) } }
            if (state.error) item { Text("Không thể tải đơn hàng. Vui lòng thử lại.", color = MaterialTheme.colorScheme.error) }
            if (!state.loading && !state.error && state.cancelError == null && state.orders.isEmpty()) item { Text("Bạn chưa có đơn hàng nào.") }
            items(state.orders, key = { it.id }) { item ->
                Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().clickable { selected = item.id }.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.code, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        OrderSummary(item)
                        Text("Xem chi tiết", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun OrderSummary(order: CustomerOrder) {
    Text("Trạng thái: ${orderStatusLabel(order.status)}", color = MaterialTheme.colorScheme.primary)
    Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN")).format(Date(order.createdAt)))
    Text("Thanh toán khi nhận hàng (COD)")
    Text("Tổng tiền: ${formatPrice(order.totalVnd)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
fun OrderContents(order: CustomerOrder) {
    Text("Người nhận", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(order.shipping.recipient)
    Text(order.shipping.phone)
    Text(order.shipping.address)
    HorizontalDivider()
    order.lines.forEach { line ->
        Text("${line.name} · ${line.volume} × ${line.quantity}")
        Text("${formatPrice(line.unitPriceVnd)} / sản phẩm · ${formatPrice(line.subtotalVnd)}")
    }
    HorizontalDivider()
    Text("Tạm tính: ${formatPrice(order.subtotalVnd)}")
    Text("Phí giao hàng: ${formatPrice(order.feeVnd)}")
    Text("Tổng COD: ${formatPrice(order.totalVnd)}", fontWeight = FontWeight.Bold)
}

fun orderErrorText(error: OrderError): String = when (error) {
    OrderError.SESSION -> "Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại."
    OrderError.REQUEST_CONFLICT -> "Yêu cầu đặt hàng đã thay đổi. Hãy quay lại kiểm tra giỏ và thông tin giao hàng."
    OrderError.CART_CHANGED -> "Giỏ hoặc địa chỉ đã thay đổi. Hãy kiểm tra và xác nhận lại."
    OrderError.STORAGE -> "Chưa xác nhận được kết quả đặt hàng. Bạn có thể thử lại cùng yêu cầu."
    OrderError.INVALID_REQUEST -> "Thông tin đặt hàng chưa hợp lệ. Hãy quay lại bước giao hàng."
    OrderError.NOT_FOUND -> "Không tìm thấy đơn hàng của tài khoản này."
    OrderError.CANNOT_CANCEL -> "Đơn đã được xử lý và không còn có thể hủy."
    OrderError.STOCK_OVERFLOW -> "Chưa thể hoàn tồn kho. Vui lòng thử lại sau."
}

private fun cancelErrorText(error: OrderError): String = if (error == OrderError.STORAGE)
    "Chưa xác nhận được kết quả hủy. Hãy tải lại đơn hoặc thử hủy lại." else orderErrorText(error)

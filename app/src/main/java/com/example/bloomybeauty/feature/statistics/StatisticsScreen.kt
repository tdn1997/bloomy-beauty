package com.example.bloomybeauty.feature.statistics

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloomybeauty.data.statistics.*
import com.example.bloomybeauty.feature.home.formatPrice
import com.example.bloomybeauty.feature.order.orderStatusLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
data class StatisticsUiState(val from: String, val through: String, val selected: StatisticsPeriod?,
    val busy: Boolean = false, val snapshot: StatisticsSnapshot? = null, val error: StatisticsError? = null)
class StatisticsViewModel(private val repository: StatisticsRepository, private val userId: Long,
    private val clock: Clock = Clock.system(STATISTICS_ZONE)) : ViewModel() {
    private val today get() = LocalDate.now(clock.withZone(STATISTICS_ZONE))
    private val initial = today.let {StatisticsPeriod(it.minusDays(29),it)}
    private val mutableState = MutableStateFlow(StatisticsUiState(initial.from.toString(),initial.through.toString(),initial))
    val state = mutableState.asStateFlow()
    private var pendingReload = false
    fun editFrom(text: String) {mutableState.value = state.value.copy(from = text,error = null)}
    fun editThrough(text: String) {mutableState.value = state.value.copy(through = text,error = null)}
    fun apply() {
        if(state.value.busy) return
        try {
            val selected = StatisticsPeriod.parse(state.value.from,state.value.through)
            mutableState.value = state.value.copy(selected = selected)
            reload()
        } catch (_: IllegalArgumentException) {mutableState.value = state.value.copy(error = StatisticsError.INVALID_RANGE)}
        catch (_: java.time.DateTimeException) {mutableState.value = state.value.copy(error = StatisticsError.INVALID_RANGE)}
    }
    fun recent(days: Long) {
        if(state.value.busy) return
        val selected = today.let {StatisticsPeriod(it.minusDays(days-1),it)}
        mutableState.value = state.value.copy(from = selected.from.toString(),through = selected.through.toString(),selected = selected)
        reload()
    }
    fun allTime() {if(!state.value.busy) {mutableState.value = state.value.copy(selected = null);reload()}}
    fun reload() {
        if(state.value.busy) {pendingReload = true;return}
        val period = state.value.selected
        mutableState.value = state.value.copy(busy = true,error = null)
        viewModelScope.launch {
            try {mutableState.value = state.value.copy(snapshot = repository.load(userId,period))}
            catch(e: CancellationException) {throw e}
            catch(e: StatisticsException) {mutableState.value = state.value.copy(error = e.error,snapshot = if(e.error == StatisticsError.PERMISSION) null else state.value.snapshot)}
            catch(_: SQLiteException) {mutableState.value = state.value.copy(error = StatisticsError.STORAGE)}
            finally {
                mutableState.value = state.value.copy(busy = false)
                if(pendingReload) {pendingReload = false;reload()}
            }
        }
    }
    class Factory(private val repository: StatisticsRepository, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StatisticsViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return StatisticsViewModel(repository,userId) as T
        }
    }
}
fun statisticsErrorText(error: StatisticsError): String = when(error) {
    StatisticsError.PERMISSION -> "Bạn không còn quyền quản trị. Vui lòng quay lại và đăng nhập lại."
    StatisticsError.INVALID_RANGE -> "Nhập ngày theo yyyy-MM-dd; ngày kết thúc không được trước ngày bắt đầu."
    StatisticsError.OVERFLOW -> "Dữ liệu hoặc khoảng thời gian vượt giới hạn tính toán."
    StatisticsError.STORAGE -> "Không thể tổng hợp dữ liệu. Vui lòng thử lại."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(state: StatisticsUiState, onBack: () -> Unit, onReload: () -> Unit,
    onFrom: (String) -> Unit, onThrough: (String) -> Unit, onApply: () -> Unit,
    onRecent: (Long) -> Unit, onAllTime: () -> Unit) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    BackHandler {onBack()}
    Scaffold(topBar = {TopAppBar(title = {Text("Thống kê bán hàng")},navigationIcon = {
        IconButton(onBack) {Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Quay lại")}
    },actions = {TextButton(onReload,enabled = !state.busy) {Text("Tải lại")}})}) {padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().testTag("statistics-list"),contentPadding = PaddingValues(24.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if(state.busy) item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.error?.let {error -> item {Text(statisticsErrorText(error),color = MaterialTheme.colorScheme.error)}}
            if(state.error == StatisticsError.PERMISSION) return@LazyColumn
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Khoảng ngày theo giờ Việt Nam",style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(state.from,onFrom,label = {Text("Từ ngày (yyyy-MM-dd)")},enabled = !state.busy,singleLine = true,modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.through,onThrough,label = {Text("Đến ngày (yyyy-MM-dd)")},enabled = !state.busy,singleLine = true,modifier = Modifier.fillMaxWidth())
                    Button({focus.clearFocus();onApply()},enabled = !state.busy) {Text("Áp dụng khoảng ngày")}
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({focus.clearFocus();onRecent(7)},enabled = !state.busy) {Text("7 ngày")}
                        TextButton({focus.clearFocus();onRecent(30)},enabled = !state.busy) {Text("30 ngày")}
                        TextButton({focus.clearFocus();onAllTime()},enabled = !state.busy) {Text("Toàn thời gian")}
                    }
                }
            }
            val report = state.snapshot ?: return@LazyColumn
            val scope = report.period?.let {"${it.from.format(dayFormat)} – ${it.through.format(dayFormat)}"} ?: "Toàn thời gian"
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Đang hiển thị: $scope",style = MaterialTheme.typography.titleMedium)
                    Text("Doanh thu tiền hàng sau giảm giá",style = MaterialTheme.typography.titleLarge)
                    Text(formatPrice(report.displayedRevenue.netVnd),style = MaterialTheme.typography.headlineSmall)
                    Text("Đơn đã giao: ${report.displayedRevenue.count}")
                    Text("Phí ship riêng: ${formatPrice(report.displayedRevenue.shippingVnd)}")
                    Text("Tiền hàng TB/đơn: ${report.averageNetVnd?.let(::formatPrice) ?: "—"}")
                    Text(if(report.period == null) "Gồm các đơn đã giao chưa có ngày giao; nhóm này được đối chiếu riêng bên dưới." else "Lọc theo ngày giao, không gồm đơn thiếu ngày giao.",style = MaterialTheme.typography.bodySmall)
                }}
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Số đơn theo ngày tạo",style = MaterialTheme.typography.titleMedium)
                    report.statusesByCreation.forEach {(status,count) -> Text("${orderStatusLabel(status)}: $count")}
                    Text("Mốc lọc này khác ngày giao dùng cho doanh thu.",style = MaterialTheme.typography.bodySmall)
                }}
            }
            item {Text("Doanh thu theo ngày giao",style = MaterialTheme.typography.titleLarge)}
            if(report.days.isEmpty()) item {Text("Chưa có đơn đã giao có ngày trong phạm vi này.")}
            val maxRevenue = report.days.maxOfOrNull {it.totals.netVnd} ?: 0
            items(report.days,key = {"day-${it.date}"}) {day ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${day.date.format(dayFormat)} · ${day.totals.count} đơn · ${formatPrice(day.totals.netVnd)}")
                    val progress = if(maxRevenue == 0L) 0f else (day.totals.netVnd.toDouble()/maxRevenue.toDouble()).toFloat().coerceIn(0f,1f)
                    LinearProgressIndicator(progress = {progress},modifier = Modifier.fillMaxWidth())
                }
            }
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Chưa có ngày giao — toàn thời gian",style = MaterialTheme.typography.titleMedium)
                    Text("${report.unknownDelivery.count} đơn · tiền hàng ${formatPrice(report.unknownDelivery.netVnd)} · ship ${formatPrice(report.unknownDelivery.shippingVnd)}")
                    Text("Không gán các đơn này vào biểu đồ ngày.",style = MaterialTheme.typography.bodySmall)
                }}
            }
            item {
                Text("Sản phẩm bán chạy",style = MaterialTheme.typography.titleLarge)
                Text(if(report.period == null) "Toàn bộ đơn đã giao, gồm đơn chưa có ngày giao." else "Theo ngày giao trong khoảng đã chọn.",style = MaterialTheme.typography.bodySmall)
                if(report.bestSellers.isEmpty()) Text("Chưa có sản phẩm đã giao trong phạm vi này.")
            }
            items(report.bestSellers,key = {"seller-${it.productId}"}) {product ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(12.dp)) {Text(product.name,Modifier.weight(1f));Text("${product.quantity} SP")}
            }
            item {Text("Sắp hết hàng — hiện tại",style = MaterialTheme.typography.titleLarge);Text("Chỉ sản phẩm đang bán, tồn kho ≤ 5.",style = MaterialTheme.typography.bodySmall)}
            if(report.lowStock.isEmpty()) item {Text("Không có sản phẩm ở ngưỡng sắp hết hàng.")}
            items(report.lowStock,key = {"stock-${it.productId}"}) {product -> Text("${product.name} · còn ${product.stock}")}
        }
    }
}

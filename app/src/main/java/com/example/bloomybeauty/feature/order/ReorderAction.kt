package com.example.bloomybeauty.feature.order

import android.database.sqlite.SQLiteException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.feature.home.formatPrice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReorderUiState(
    val orderId: Long? = null,
    val preview: ReorderPreview? = null,
    val busy: Boolean = false,
    val error: ReorderError? = null,
)

class ReorderViewModel(private val repository: ReorderRepository, private val userId: Long) : ViewModel() {
    private val mutableState = MutableStateFlow(ReorderUiState())
    val state = mutableState.asStateFlow()

    fun preview(orderId: Long) {
        if (state.value.busy) return
        mutableState.value = ReorderUiState(orderId = orderId, busy = true)
        viewModelScope.launch {
            try { mutableState.value = ReorderUiState(orderId, repository.preview(userId, orderId)) }
            catch (e: ReorderException) { mutableState.value = ReorderUiState(orderId, error = e.error) }
            catch (e: SQLiteException) { mutableState.value = ReorderUiState(orderId, error = ReorderError.STORAGE) }
        }
    }

    fun dismiss() { if (!state.value.busy) mutableState.value = ReorderUiState() }

    fun confirm(onAdded: (ReorderPreview) -> Unit) {
        val preview = state.value.preview ?: return
        if (state.value.busy || !preview.canConfirm) return
        mutableState.value = state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val result = repository.confirm(userId, preview)
                mutableState.value = ReorderUiState()
                onAdded(result)
            } catch (e: ReorderException) {
                if (e.error == ReorderError.CHANGED) {
                    try {
                        mutableState.value = ReorderUiState(preview.orderId, repository.preview(userId, preview.orderId), error = ReorderError.CHANGED)
                    } catch (refresh: ReorderException) { mutableState.value = ReorderUiState(preview.orderId, error = refresh.error) }
                    catch (refresh: SQLiteException) { mutableState.value = ReorderUiState(preview.orderId, error = ReorderError.STORAGE) }
                } else mutableState.value = ReorderUiState(preview.orderId, error = e.error)
            } catch (e: SQLiteException) {
                // A retry starts with a new preview; never blindly replay a possibly committed merge.
                mutableState.value = ReorderUiState(preview.orderId, error = ReorderError.STORAGE)
            }
        }
    }

    class Factory(private val repository: ReorderRepository, private val userId: Long) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ReorderViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return ReorderViewModel(repository, userId) as T
        }
    }
}

@Composable
fun ReorderAction(order: CustomerOrder, model: ReorderViewModel, enabled: Boolean,
                  onAdded: (ReorderPreview) -> Unit, onOpenCart: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val relevant = state.orderId == order.id
    Button({ model.preview(order.id) }, enabled = enabled && !state.busy,
        modifier = Modifier.fillMaxWidth().testTag("reorder-open")) {
        Text(if (state.busy && relevant) "Đang xử lý…" else "Mua lại")
    }
    if (relevant && state.preview == null) state.error?.let {
        Text(reorderErrorText(it), color = MaterialTheme.colorScheme.error)
        if (it == ReorderError.CHECKOUT_PENDING || it == ReorderError.STORAGE) {
            TextButton(onOpenCart) { Text("Kiểm tra giỏ hàng") }
        }
    }
    if (relevant) state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = model::dismiss,
            title = { Text("Mua lại đơn ${order.code}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).testTag("reorder-preview"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sản phẩm sẽ được gộp vào giỏ hiện tại theo giá hiện tại. Bạn kiểm tra lại giao hàng và ưu đãi khi đặt đơn mới.")
                    state.error?.let { Text(reorderErrorText(it), color = MaterialTheme.colorScheme.error) }
                    preview.lines.forEach { line ->
                        Column(Modifier.testTag("reorder-line-${line.productId}")) {
                            Text(line.name, style = MaterialTheme.typography.titleSmall)
                            Text("Yêu cầu ${line.requested} · Thêm ${line.added} · Giỏ hiện có ${line.existing}")
                            line.currentPriceVnd?.let { current ->
                                Text(if (current != line.oldPriceVnd) "Giá cũ ${formatPrice(line.oldPriceVnd)} → Hiện tại ${formatPrice(current)}" else "Giá hiện tại: ${formatPrice(current)}")
                            }
                            line.reason?.let { Text(reorderReasonText(it), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    Text("Thêm ${preview.addedQuantity} sản phẩm · Tạm tính ${formatPrice(preview.subtotalVnd)}")
                    Text("Chưa gồm phí giao hàng và giảm giá.")
                    if (preview.addedQuantity == 0) Text("Không có sản phẩm nào có thể thêm vào giỏ.")
                    if (preview.needsPriceAcceptance) TextButton({ model.dismiss(); onOpenCart() }, enabled = !state.busy) { Text("Vào giỏ xác nhận giá mới") }
                }
            },
            confirmButton = { TextButton({ model.confirm(onAdded) }, enabled = enabled && preview.canConfirm && !state.busy,
                modifier = Modifier.testTag("reorder-confirm")) { Text(if (state.busy) "Đang thêm…" else "Thêm vào giỏ") } },
            dismissButton = { TextButton(model::dismiss, enabled = !state.busy) { Text("Hủy") } },
        )
    }
}

fun reorderSummary(preview: ReorderPreview): String {
    val skipped = preview.lines.count { it.added == 0 }
    val reduced = preview.lines.count { it.added in 1 until it.requested }
    return "Đã thêm ${preview.addedQuantity} sản phẩm vào giỏ" +
        (if (skipped > 0) "; bỏ qua $skipped mặt hàng" else "") +
        (if (reduced > 0) "; $reduced mặt hàng được giảm số lượng" else "") + "."
}

fun reorderReasonText(reason: ReorderReason): String = when (reason) {
    ReorderReason.MISSING -> "Sản phẩm không còn tồn tại."
    ReorderReason.INACTIVE -> "Sản phẩm đã ngừng bán."
    ReorderReason.OUT_OF_STOCK -> "Sản phẩm đã hết hàng."
    ReorderReason.LIMIT -> "Giỏ đã đạt tồn kho hoặc giới hạn 99 sản phẩm."
    ReorderReason.CART_INVALID -> "Số lượng trong giỏ vượt tồn kho. Hãy điều chỉnh trong giỏ."
    ReorderReason.ACCEPT_PRICE -> "Giá của sản phẩm trong giỏ đã thay đổi. Hãy xác nhận giá mới trong giỏ trước."
    ReorderReason.PARTIAL -> "Chỉ thêm được một phần do tồn kho hoặc giới hạn 99 sản phẩm."
}

fun reorderErrorText(error: ReorderError): String = when (error) {
    ReorderError.SESSION -> "Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại."
    ReorderError.NOT_FOUND -> "Không tìm thấy đơn hàng của tài khoản này."
    ReorderError.STATUS -> "Chỉ mua lại đơn đã giao hoặc đã hủy."
    ReorderError.CHECKOUT_PENDING -> "Bạn có kết quả đặt hàng chưa xem xong. Hãy vào giỏ và hoàn tất màn kết quả trước khi mua lại."
    ReorderError.CHANGED -> "Giỏ hoặc sản phẩm đã thay đổi. Nội dung đã được cập nhật; hãy kiểm tra và xác nhận lại."
    ReorderError.USED -> "Lần mua lại này đã được xử lý. Hãy kiểm tra giỏ trước khi mua lại lần nữa."
    ReorderError.EMPTY -> "Không có sản phẩm hợp lệ để thêm. Hãy kiểm tra giỏ và thử lại."
    ReorderError.STORAGE -> "Không thể xác nhận kết quả. Hãy kiểm tra giỏ trước khi thử lại."
    ReorderError.TOTAL_OVERFLOW -> "Tổng tiền vượt giới hạn xử lý. Hãy kiểm tra lại sản phẩm trong giỏ."
}

package com.example.bloomybeauty.feature.export

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.bloomybeauty.export.OrderPdfExporter
import com.example.bloomybeauty.export.PdfAccessException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun OrderPdfActions(exporter: OrderPdfExporter, userId: Long, orderId: Long) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(userId,orderId) {mutableStateOf(false)}
    var error by remember(userId,orderId) {mutableStateOf<String?>(null)}
    fun export(share: Boolean) {
        if(busy) return
        busy = true;error = null
        scope.launch {
            try {exporter.launch(exporter.export(userId,orderId),context,share)}
            catch(e: CancellationException) {throw e}
            catch(_: PdfAccessException) {error = "Không còn quyền xuất đơn này. Hãy tải lại hoặc đăng nhập lại."}
            catch(_: ActivityNotFoundException) {error = "Chưa có ứng dụng mở PDF. Bạn có thể thử chia sẻ phiếu."}
            catch(_: Exception) {error = "Không thể tạo hoặc mở PDF. Vui lòng thử lại."}
            finally {busy = false}
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Phiếu đơn hàng — bản demo",style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton({export(false)},enabled=!busy) {Text("Xem PDF")}
            OutlinedButton({export(true)},enabled=!busy) {Text("Chia sẻ PDF")}
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    }
}

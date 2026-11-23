package com.example.bloomybeauty.feature.export

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.bloomybeauty.export.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun ProductShareActions(exporter: ProductImageExporter, userId: Long, productId: String) {
    key(userId, productId, exporter) { ProductShareContent(exporter, userId, productId) }
}

@Composable
private fun ProductShareContent(exporter: ProductImageExporter, userId: Long, productId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var artifact by remember { mutableStateOf<ProductImageArtifact?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun dismiss() {
        if (busy) return
        artifact?.let(exporter::discard)
        artifact = null; preview = null
    }
    DisposableEffect(exporter) { onDispose { artifact?.let(exporter::discard) } }
    fun create() {
        if (busy) return
        busy = true; error = null
        scope.launch {
            var pending: ProductImageArtifact? = null
            try {
                val created = exporter.export(userId, productId).also { pending = it }
                val bitmap = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(created.file.path)?.asImageBitmap() ?: throw IOException("Cannot decode product image")
                }
                artifact = created; preview = bitmap; pending = null
            } catch (e: CancellationException) { throw e }
            catch (e: ProductImageException) { error = productImageErrorText(e.error) }
            catch (_: Exception) { error = "Không thể tạo ảnh sản phẩm. Vui lòng thử lại." }
            finally { pending?.let(exporter::discard); busy = false }
        }
    }
    fun share() {
        val current = artifact ?: return
        if (busy) return
        busy = true; error = null
        scope.launch {
            try {
                val intent = exporter.shareIntent(current)
                val chooser = Intent.createChooser(intent, "Chia sẻ ảnh sản phẩm").apply {
                    clipData = intent.clipData
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(chooser)
                // Receivers may read asynchronously. Keep the granted file until cache cleanup.
                artifact = null; preview = null
            } catch (e: CancellationException) { throw e }
            catch (_: ActivityNotFoundException) { error = "Không tìm thấy ứng dụng nhận ảnh. Bạn có thể thử lại." }
            catch (e: Exception) {
                error = if (e is ProductImageException) productImageErrorText(e.error) else "Không thể mở ảnh chia sẻ. Hãy tạo lại ảnh."
                exporter.discard(current); artifact = null; preview = null
            } finally { busy = false }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(::create, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("product-share-create")) {
            Text(if (busy) "Đang tạo ảnh…" else "Chia sẻ ảnh sản phẩm")
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (artifact == null) error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    val image = preview
    if (artifact != null && image != null) AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text("Xem trước ảnh chia sẻ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(image, "Ảnh chia sẻ ${artifact?.productName.orEmpty()}",
                    Modifier.fillMaxWidth().heightIn(max = 400.dp).testTag("product-share-preview"), contentScale = ContentScale.Fit)
                Text("Ảnh chứa thông tin sản phẩm và giá tham khảo tại thời điểm tạo.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(::share, enabled = !busy, modifier = Modifier.testTag("product-share-confirm")) { Text(if (busy) "Đang mở…" else "Chia sẻ ảnh") } },
        dismissButton = { TextButton(::dismiss, enabled = !busy) { Text("Hủy") } },
    )
}

fun productImageErrorText(error: ProductImageError): String = when (error) {
    ProductImageError.SESSION -> "Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại."
    ProductImageError.UNAVAILABLE -> "Sản phẩm không còn được bán. Hãy tải lại danh mục."
    ProductImageError.CHANGED -> "Thông tin sản phẩm đã thay đổi. Hãy tạo lại ảnh để chia sẻ."
    ProductImageError.EXPIRED -> "Ảnh tạm đã hết hạn. Hãy tạo lại ảnh để chia sẻ."
}

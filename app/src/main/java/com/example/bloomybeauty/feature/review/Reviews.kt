package com.example.bloomybeauty.feature.review

import android.database.sqlite.SQLiteException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.bloomybeauty.data.review.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private fun failure(e: Exception): String = when(e) {
    is ReviewException -> e.message.orEmpty()
    is SQLiteException -> "Không thể tải hoặc lưu đánh giá. Vui lòng thử lại."
    else -> "Không thể xử lý đánh giá. Vui lòng thử lại."
}

@Composable
fun ReviewEditor(repository: ReviewRepository, userId: Long, orderId: Long, productId: String, onClose: () -> Unit) {
    var review by remember { mutableStateOf<ProductReview?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rating by rememberSaveable { mutableIntStateOf(5) }
    var comment by rememberSaveable { mutableStateOf("") }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var expectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var expectedRevision by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope { Dispatchers.Main.immediate }
    suspend fun load() {
        busy = true; error = null
        try {
            review = repository.own(userId, orderId, productId)
            if (!initialized) {
                rating = review?.rating ?: 5; comment = review?.comment.orEmpty()
                expectedId = review?.id; expectedRevision = review?.revision; initialized = true
            }
            loaded = true
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = failure(e) }
        finally { busy = false }
    }
    LaunchedEffect(repository, userId, orderId, productId) { withContext(Dispatchers.Main.immediate) { load() } }
    AlertDialog(
        onDismissRequest = { if (!busy) { if (confirmDelete) confirmDelete = false else onClose() } },
        title = { Text(if (confirmDelete) "Xóa đánh giá?" else if (review == null) "Đánh giá sản phẩm" else "Sửa đánh giá") },
        text = {
            val focus = androidx.compose.ui.platform.LocalFocusManager.current
            if (confirmDelete) Text("Bạn có thể viết lại đánh giá sau khi xóa.")
            else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Đánh giá từ đơn hàng đã giao")
                Row {
                    (1..5).forEach { star ->
                        TextButton({ rating = star }, enabled = loaded && !busy, contentPadding = PaddingValues(4.dp),
                            modifier = Modifier.weight(1f).semantics { contentDescription = "$star sao" }) {
                            Text(if (star <= rating) "★" else "☆")
                        }
                    }
                }
                Text("$rating / 5 sao")
                OutlinedTextField(comment, { if (it.length <= 1000) comment = it }, enabled = loaded && !busy,
                    label = { Text("Nhận xét") }, supportingText = { Text("${comment.length}/1000") },
                    modifier = Modifier.fillMaxWidth().testTag("review-comment"), minLines = 3, maxLines = 5)
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton({ scope.launch { initialized = false; load() } }, enabled = !busy) { Text("Tải lại đánh giá") }
                }
                if (review != null) TextButton({ focus.clearFocus(); confirmDelete = true }, enabled = !busy && expectedId != null) { Text("Xóa đánh giá") }
            }
        },
        confirmButton = {
            val focus = androidx.compose.ui.platform.LocalFocusManager.current
            TextButton(onClick = {
                focus.clearFocus()
                scope.launch {
                    busy = true; error = null
                    try {
                        if (confirmDelete) repository.delete(userId, checkNotNull(review).copy(
                            id = checkNotNull(expectedId), revision = checkNotNull(expectedRevision)))
                        else repository.save(userId, orderId, productId, rating, comment,
                            expectedId?.let { ReviewToken(it, checkNotNull(expectedRevision)) })
                        onClose()
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = failure(e) }
                    finally { busy = false; confirmDelete = false }
                }
            }, enabled = !busy && (confirmDelete || (loaded && comment.isNotBlank()))) {
                Text(if (confirmDelete) "Xóa" else "Lưu đánh giá")
            }
        },
        dismissButton = {
            TextButton({ if (confirmDelete) confirmDelete = false else onClose() }, enabled = !busy) {
                Text(if (confirmDelete) "Giữ lại" else "Đóng")
            }
        },
    )
}

@Composable
fun ProductReviews(repository: ReviewRepository, userId: Long, productId: String) {
    var page by remember(productId) { mutableStateOf<ReviewPage?>(null) }
    var reviews by remember(productId) { mutableStateOf(emptyList<ProductReview>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope { Dispatchers.Main.immediate }
    suspend fun load(reset: Boolean) {
        busy = true; error = null
        try { val next = repository.list(userId, productId, if(reset) 0 else reviews.size); page = next; reviews = if(reset) next.reviews else (reviews + next.reviews).distinctBy {it.id} }
        catch(e: CancellationException) {throw e}
        catch(e: Exception) {error = failure(e)}
        finally {busy = false}
    }
    LaunchedEffect(repository, userId, productId) { withContext(Dispatchers.Main.immediate) { load(true) } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Đánh giá sản phẩm", style = MaterialTheme.typography.titleLarge)
        page?.let { Text(if(it.count == 0) "Chưa có đánh giá. Bạn có thể đánh giá từ đơn đã giao." else String.format(Locale.forLanguageTag("vi-VN"), "%.1f / 5 sao · %d đánh giá", it.average, it.count)) }
        reviews.forEach { review ->
            HorizontalDivider()
            Text("${review.author} · ${review.rating}/5 sao", style = MaterialTheme.typography.titleSmall)
            Text(review.comment)
            Text("Đã mua hàng", style = MaterialTheme.typography.labelSmall)
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {Text(it, color = MaterialTheme.colorScheme.error)}
        Row {
            TextButton({scope.launch {load(true)}}, enabled = !busy) {Text("Tải lại đánh giá")}
            if(reviews.size < (page?.count ?: 0)) TextButton({scope.launch {load(false)}}, enabled = !busy) {Text("Xem thêm")}
        }
    }
}

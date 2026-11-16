package com.example.bloomybeauty.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bloomybeauty.data.catalog.Product

object ComparisonSelection {
    const val LIMIT = 3
    fun reconcile(ids: List<String>, products: List<Product>): List<String> =
        ids.distinct().filter { id -> products.any { it.id == id && it.active } }.take(LIMIT)
    fun toggle(ids: List<String>, id: String, products: List<Product>): List<String> {
        val current = reconcile(ids, products)
        return when {
            id in current -> current - id
            current.size == LIMIT || products.none { it.id == id && it.active } -> current
            else -> current + id
        }
    }
    fun information(text: String): String = text.ifBlank { "Chưa có thông tin" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(products: List<Product>, categories: Map<String, String>, loading: Boolean, error: Boolean,
    onBack: () -> Unit, onRetry: () -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit,
    onDetail: (String) -> Unit, onAdd: (String) -> Unit, cartBusy: Boolean, snackbar: SnackbarHostState) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = {
        TopAppBar(title = { Text("So sánh sản phẩm (${products.size}/3)", fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Quay lại") } },
            actions = {
                IconButton(onRetry, enabled = !loading) { Icon(Icons.Outlined.Refresh, "Tải lại dữ liệu so sánh") }
                TextButton(onClear, enabled = products.isNotEmpty()) { Text("Xóa tất cả", fontSize = 12.sp) }
            })
    }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                loading -> CircularProgressIndicator()
                error -> { Text("Không thể tải dữ liệu so sánh."); TextButton(onRetry) { Text("Thử lại") } }
                products.isEmpty() -> {
                    Text("Chưa có sản phẩm để so sánh.")
                    Button(onBack) { Text("Chọn sản phẩm") }
                }
                else -> {
                    Text("Chọn 2–3 sản phẩm. Vuốt ngang để xem các cột, cuộn dọc để xem đầy đủ thông tin.", fontSize = 12.sp, lineHeight = 16.sp)
                    if (products.size < 2) Text("Cần ít nhất 2 sản phẩm để đối chiếu. Quay lại danh mục để chọn thêm.")
                    Text("Giá và tồn kho demo trên thiết bị. Giữ lựa chọn khi chuyển màn hình hoặc xoay máy.", fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val columnWidth = maxOf(132.dp, (maxWidth - 76.dp) / products.size)
                        Column(Modifier.horizontalScroll(rememberScrollState()).testTag("comparison-table")) {
                            Row {
                                Text("Sản phẩm", Modifier.width(76.dp).padding(6.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp)
                                products.forEach { product ->
                                    Column(Modifier.width(columnWidth).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Image(painterResource(productImage(product.imageKey)), null, Modifier.size(64.dp))
                                        Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, minLines = 2)
                                        Column(
                                            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            val buttonPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                            val buttonShape = RoundedCornerShape(10.dp)
                                            TextButton(
                                                { onRemove(product.id) },
                                                Modifier.fillMaxWidth().heightIn(min = 40.dp).testTag("compare-remove-${product.id}"),
                                                shape = buttonShape, contentPadding = buttonPadding,
                                            ) { Text("Bỏ khỏi so sánh", fontSize = 12.sp, lineHeight = 16.sp) }
                                            OutlinedButton(
                                                { onDetail(product.id) },
                                                Modifier.fillMaxWidth().heightIn(min = 40.dp).testTag("compare-detail-${product.id}"),
                                                shape = buttonShape, contentPadding = buttonPadding,
                                            ) { Text("Xem chi tiết", fontSize = 12.sp, lineHeight = 16.sp) }
                                            Button(
                                                { onAdd(product.id) }, enabled = product.active && product.stock > 0 && !cartBusy,
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).testTag("compare-add-${product.id}"),
                                                shape = buttonShape, contentPadding = buttonPadding,
                                            ) { Text("Thêm vào giỏ", fontSize = 12.sp, lineHeight = 16.sp) }
                                        }
                                    }
                                }
                            }
                            ComparisonRow("Thương hiệu", products.map { it.brand }, columnWidth)
                            ComparisonRow("Danh mục", products.map { categories[it.categoryId].orEmpty() }, columnWidth)
                            ComparisonRow("Giá hiện tại", products.map { formatPrice(it.priceVnd) }, columnWidth)
                            ComparisonRow("Dung tích", products.map { it.volume }, columnWidth)
                            ComparisonRow("Tình trạng", products.map { if (it.stock == 0) "Hết hàng" else "Còn ${it.stock} sản phẩm" }, columnWidth)
                            ComparisonRow("Công dụng / mô tả", products.map { it.description }, columnWidth)
                            ComparisonRow("Thành phần", products.map { it.ingredients }, columnWidth)
                            ComparisonRow("Cách dùng", products.map { it.usage }, columnWidth)
                            ComparisonRow("Loại da phù hợp", products.map { "" }, columnWidth)
                        }
                    }
                    Text("Thông tin loại da phù hợp chưa được cung cấp. Giá hiển thị theo từng sản phẩm, chưa quy đổi theo ml hoặc g.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ComparisonRow(label: String, values: List<String>, columnWidth: Dp) {
    HorizontalDivider(Modifier.width(76.dp + columnWidth * values.size))
    Row {
        Text(label, Modifier.width(76.dp).padding(6.dp), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp)
        values.forEach { Text(ComparisonSelection.information(it), Modifier.width(columnWidth).padding(6.dp), fontSize = 13.sp, lineHeight = 18.sp) }
    }
}

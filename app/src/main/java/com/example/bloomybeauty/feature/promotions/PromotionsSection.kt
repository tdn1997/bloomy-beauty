package com.example.bloomybeauty.feature.promotions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.bloomybeauty.data.catalog.Product
import com.example.bloomybeauty.data.promotions.*
import com.example.bloomybeauty.feature.home.formatPrice
import java.util.Locale

@Composable
fun PromotionsSection(state: PromotionsUiState, products: List<Product>, onRetry: () -> Unit, onOpenProduct: (String) -> Unit) {
    if (!state.feed.enabled) return
    val clipboard = LocalClipboardManager.current
    val sales = state.flashSales.mapNotNull { sale ->
        products.firstOrNull { it.id == sale.productId && it.active && sale.salePriceVnd < it.priceVnd }
            ?.let { sale to it }
    }
    Column(Modifier.fillMaxWidth().testTag("promotions"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Ưu đãi mới nhất", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        when {
            state.feed.error != null -> {
                Text(if (state.feed.error == PromotionsError.PERMISSION_DENIED)
                    "Chưa thể tải ưu đãi. Vui lòng thử lại sau." else "Không thể cập nhật ưu đãi. Kiểm tra kết nối mạng.",
                    color = MaterialTheme.colorScheme.error)
                TextButton(onRetry) { Text("Tải lại ưu đãi") }
            }
            state.feed.loading -> Text("Đang tải ưu đãi…")
            state.feed.fromCache -> Text("Đang hiển thị dữ liệu đã lưu. Kết nối mạng để xác nhận ưu đãi.")
            sales.isEmpty() && state.coupons.isEmpty() -> Text("Chưa có chương trình ưu đãi đang mở.")
        }
        sales.forEach { (sale, product) ->
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("FLASH SALE · ${sale.title}", fontWeight = FontWeight.Bold)
                    Text(product.name)
                    Text("Giá chương trình: ${formatPrice(sale.salePriceVnd)}", style = MaterialTheme.typography.titleMedium)
                    val upcoming = sale.phase(state.now) == PromotionPhase.UPCOMING
                    Text((if (upcoming) "Bắt đầu sau " else "Kết thúc sau ") +
                        countdown((if (upcoming) sale.startsAt else sale.endsAt) - state.now))
                    TextButton({ onOpenProduct(product.id) }) { Text("Xem sản phẩm") }
                }
            }
        }
        state.coupons.forEach { coupon ->
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(coupon.title, fontWeight = FontWeight.Bold)
                    Text(coupon.code, style = MaterialTheme.typography.titleLarge)
                    Text(if (coupon.discountType == DiscountType.PERCENT)
                        "Giảm ${coupon.discountValue}% · tối đa ${formatPrice(coupon.maxDiscountVnd ?: 0)}"
                        else "Giảm ${formatPrice(coupon.discountValue)}")
                    Text("Đơn từ ${formatPrice(coupon.minOrderVnd)} · còn ${countdown(coupon.endsAt - state.now)}")
                    OutlinedButton(onClick = {
                        if (coupon.phase(System.currentTimeMillis()) == PromotionPhase.ACTIVE) {
                            clipboard.setText(AnnotatedString(coupon.code))
                        }
                    }, enabled = !state.feed.fromCache && state.feed.error == null) { Text("Sao chép ${coupon.code}") }
                }
            }
        }
        if (sales.isNotEmpty() || state.coupons.isNotEmpty()) {
            Text("Thông tin chương trình để tham khảo. Giỏ hàng hiện chưa áp dụng giá flash sale hoặc mã giảm giá.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal fun countdown(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) + 999) / 1_000
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}

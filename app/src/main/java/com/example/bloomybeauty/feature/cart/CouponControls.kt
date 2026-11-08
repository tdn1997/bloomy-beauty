package com.example.bloomybeauty.feature.cart

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.bloomybeauty.data.coupon.*
import com.example.bloomybeauty.feature.home.formatPrice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CouponControls(state: CartUiState, onChange: (String) -> Unit, onApply: () -> Unit, onRemove: () -> Unit) {
    if(!state.couponsEnabled) return
    val focus = LocalFocusManager.current
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {mutableStateOf(TextFieldValue(state.couponText))}
    LaunchedEffect(state.couponText) {if(value.text != state.couponText) value = TextFieldValue(state.couponText)}
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ưu đãi demo",style = MaterialTheme.typography.titleMedium)
            Text("Mã mẫu cho bản trình diễn. Chọn một mã cho mỗi đơn.",style = MaterialTheme.typography.bodySmall)
            state.couponRules.forEach {rule ->
                val amount = if(rule.kind == CouponKind.PERCENT) "${rule.value}%" else formatPrice(rule.value)
                val cap = rule.maximumDiscount?.let {", tối đa ${formatPrice(it)}"}.orEmpty()
                val until = SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.forLanguageTag("vi-VN")).format(Date(rule.endsAt))
                Text("${rule.code}: giảm $amount$cap; đơn từ ${formatPrice(rule.minimumSubtotal)}; hết hạn trước $until.",style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(value,{if(it.text.length <= 32) {value = it;onChange(it.text)}},
                label = {Text("Mã giảm giá demo")},singleLine = true,enabled = !state.busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({focus.clearFocus();onApply()}, enabled = !state.busy && (state.couponText.isNotBlank() || state.request?.coupon != null || state.quoteInvalid)) {Text("Áp dụng")}
                TextButton({focus.clearFocus();onRemove()}, enabled = !state.busy && (state.couponText.isNotBlank() || state.request?.coupon != null)) {Text("Không dùng mã")}
            }
            state.couponError?.let {Text(couponErrorText(it),color = MaterialTheme.colorScheme.error)}
            if(state.request != null && normalizeCoupon(state.couponText) != state.request.coupon?.rule?.code.orEmpty())
                Text("Nhấn Áp dụng để cập nhật thay đổi mã trước khi đặt hàng.",style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun couponErrorText(error: CouponException): String = when(error.error) {
    CouponError.DISABLED -> "Mã mẫu chỉ áp dụng trong bản trình diễn."
    CouponError.NOT_FOUND -> "Không tìm thấy mã giảm giá này."
    CouponError.INACTIVE -> "Mã này đã ngừng phát hành. Hãy chọn mã khác hoặc chủ động bỏ mã."
    CouponError.NOT_STARTED -> "Mã chưa đến thời gian áp dụng."
    CouponError.EXPIRED -> "Mã đã hết hạn. Hãy chọn mã khác hoặc chủ động bỏ mã và xác nhận lại."
    CouponError.MINIMUM -> "Đơn cần đạt ${formatPrice(error.minimumSubtotal ?: 0)} tiền hàng trước giảm để dùng mã."
    CouponError.INVALID_RULE -> "Chưa thể áp dụng mã này. Vui lòng chọn mã khác."
}

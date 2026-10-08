package com.example.bloomybeauty.feature.cart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.feature.home.formatPrice
import com.example.bloomybeauty.feature.home.productImage
import com.example.bloomybeauty.feature.order.OrderContents
import com.example.bloomybeauty.feature.order.OrderSummary
import com.example.bloomybeauty.feature.order.orderErrorText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    state: CartUiState,
    onBack: () -> Unit,
    onQuantityChange: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onAcceptPrices: () -> Unit,
    onContinue: () -> Unit,
    onDraftChange: (ShippingDetails) -> Unit,
    onSaveShipping: () -> Unit,
    onRetry: () -> Unit,
    onPlaceOrder: () -> Unit = {},
    onFinishOrder: () -> Unit = {},
) {
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(when (state.step) {
                CartStep.ITEMS -> "Giỏ hàng"
                CartStep.SHIPPING -> "Thông tin giao hàng"
                CartStep.REVIEW -> "Kiểm tra thông tin"
                CartStep.SUCCESS -> if (state.placedOrder?.status == "CANCELLED") "Đơn đã hủy" else "Đặt hàng thành công"
            }) }, navigationIcon = {
                IconButton(onBack, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Quay lại") }
            })
        },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            when (state.step) {
                CartStep.ITEMS -> CartItems(state, onQuantityChange, onRemove, onAcceptPrices, onContinue, onRetry, Modifier.padding(padding))
                CartStep.SHIPPING -> ShippingForm(state, onDraftChange, onSaveShipping, Modifier.padding(padding))
                CartStep.REVIEW -> ShippingReview(state, onPlaceOrder, Modifier.padding(padding))
                CartStep.SUCCESS -> Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(if (state.placedOrder?.status == "CANCELLED") "Đơn đã hủy" else "Đặt hàng thành công", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    state.placedOrder?.let { order ->
                        Text("Mã đơn: ${order.code}", style = MaterialTheme.typography.titleLarge)
                        OrderSummary(order)
                        OrderContents(order)
                    }
                    state.error?.let { CartErrorMessage(it) }
                    Button(onFinishOrder, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Xem đơn hàng của tôi") }
                }
            }
        }
    }
}

@Composable
private fun CartItems(state: CartUiState, onQuantity: (String, Int) -> Unit, onRemove: (String) -> Unit,
                      onAcceptPrices: () -> Unit, onContinue: () -> Unit, onRetry: () -> Unit, modifier: Modifier) {
    val snapshot = state.snapshot
    val total = try { snapshot.totalVnd } catch (exception: ArithmeticException) { null }
    LazyColumn(modifier.fillMaxSize().testTag("cart_items"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("${snapshot.quantity} sản phẩm trong giỏ", style = MaterialTheme.typography.titleMedium)
            Text("Tồn kho và giá phục vụ bản demo trên thiết bị.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let { error -> item {
            CartErrorMessage(error)
            if (error == CartError.STORAGE) TextButton(onRetry, enabled = !state.busy) { Text("Thử lại") }
        } }
        state.orderError?.let { error -> item { Text(orderErrorText(error), color = MaterialTheme.colorScheme.error) } }
        if (snapshot.items.isEmpty()) item {
            Text("Giỏ hàng đang trống. Hãy chọn sản phẩm ở trang chủ.", Modifier.padding(vertical = 32.dp))
        }
        items(snapshot.items, key = { it.product.id }) { item ->
            Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(productImage(item.product.imageKey)), null, Modifier.size(80.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.product.name, style = MaterialTheme.typography.titleMedium)
                            Text(item.product.volume, style = MaterialTheme.typography.bodySmall)
                            Text(formatPrice(item.product.priceVnd), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (item.priceChanged) Text("Giá đã đổi từ ${formatPrice(item.acceptedPriceVnd)}. Vui lòng xác nhận giá mới.", color = MaterialTheme.colorScheme.error)
                    if (!item.product.active) Text("Sản phẩm đã ngừng bán. Vui lòng xóa khỏi giỏ.", color = MaterialTheme.colorScheme.error)
                    else if (item.product.stock == 0) Text("Sản phẩm đã hết hàng. Vui lòng xóa khỏi giỏ.", color = MaterialTheme.colorScheme.error)
                    else Text("Tồn kho demo: ${item.product.stock}", style = MaterialTheme.typography.bodySmall)
                    if (item.quantity > item.product.stock && item.product.active && item.product.stock > 0) {
                        Text("Số lượng vượt tồn kho hiện tại.", color = MaterialTheme.colorScheme.error)
                        TextButton({ onQuantity(item.product.id, minOf(item.product.stock, MAX_CART_QUANTITY)) }, enabled = !state.busy) { Text("Giảm về tồn kho hiện tại") }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ onQuantity(item.product.id, item.quantity - 1) }, modifier = Modifier.testTag("decrease_${item.product.id}"), enabled = !state.busy && item.quantity > 1 && item.product.active) {
                            Text("−")
                        }
                        Text(item.quantity.toString(), Modifier.testTag("quantity_${item.product.id}"), fontWeight = FontWeight.Bold)
                        TextButton({ onQuantity(item.product.id, item.quantity + 1) }, modifier = Modifier.testTag("increase_${item.product.id}"), enabled = !state.busy && item.product.active && item.quantity < minOf(item.product.stock, MAX_CART_QUANTITY)) {
                            Text("+")
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton({ onRemove(item.product.id) }, modifier = Modifier.testTag("remove_${item.product.id}"), enabled = !state.busy) { Text("Xóa") }
                    }
                }
            }
        }
        if (snapshot.items.any { it.priceChanged }) item {
            OutlinedButton(onAcceptPrices, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Xác nhận giá mới cho giỏ hàng") }
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tạm tính", style = MaterialTheme.typography.titleMedium)
                Text(total?.let(::formatPrice) ?: "Không thể tính tổng", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Text("Chưa bao gồm phí giao hàng.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Button(onContinue, enabled = snapshot.canContinue && total != null && !state.busy && state.error != CartError.STORAGE, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.busy) "Đang kiểm tra…" else "Tiếp tục giao hàng")
            }
        }
    }
}

@Composable
private fun ShippingForm(state: CartUiState, onChange: (ShippingDetails) -> Unit, onSave: () -> Unit, modifier: Modifier) {
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Địa chỉ của bạn", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Thông tin đã lưu sẽ được dùng lại lần sau. Bạn có thể sửa trước khi tiếp tục.")
        ShippingField("Người nhận", state.draft.recipient, { onChange(state.draft.copy(recipient = it)) }, !state.busy,
            state.shippingError == ShippingError.NAME, KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
        ShippingField("Số điện thoại", state.draft.phone, { onChange(state.draft.copy(phone = it)) }, !state.busy,
            state.shippingError == ShippingError.PHONE, KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
        Text("Số di động Việt Nam gồm 10 chữ số hoặc bắt đầu bằng +84.", style = MaterialTheme.typography.bodySmall)
        ShippingField("Địa chỉ nhận hàng", state.draft.address, { onChange(state.draft.copy(address = it)) }, !state.busy,
            state.shippingError == ShippingError.ADDRESS, KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), singleLine = false)
        Text("Gồm số nhà, đường, phường/xã và tỉnh/thành phố.", style = MaterialTheme.typography.bodySmall)
        state.shippingError?.let {
            Text(when (it) {
                ShippingError.NAME -> "Người nhận cần từ 2 đến 80 ký tự."
                ShippingError.PHONE -> "Vui lòng nhập số di động Việt Nam hợp lệ."
                ShippingError.ADDRESS -> "Địa chỉ cần từ 10 đến 300 ký tự."
            }, color = MaterialTheme.colorScheme.error)
        }
        state.error?.let { CartErrorMessage(it) }
        Button({ focus.clearFocus(); onSave() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Đang lưu…" else "Lưu và kiểm tra thông tin")
        }
    }
}

@Composable
private fun ShippingField(label: String, value: String, onChange: (String) -> Unit, enabled: Boolean, error: Boolean,
                          options: KeyboardOptions, singleLine: Boolean = true) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) { if (field.text != value) field = TextFieldValue(value) }
    OutlinedTextField(field, { field = it; onChange(it.text) }, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        enabled = enabled, isError = error, singleLine = singleLine, minLines = if (singleLine) 1 else 3,
        keyboardOptions = options, shape = RoundedCornerShape(14.dp))
}

@Composable
private fun ShippingReview(state: CartUiState, onPlace: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Thông tin giao hàng đã lưu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.snapshot.shipping.recipient, fontWeight = FontWeight.Bold)
                Text(state.snapshot.shipping.phone)
                Text(state.snapshot.shipping.address)
            }
        }
        state.snapshot.items.forEach { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${item.product.name} × ${item.quantity}", Modifier.weight(1f))
                Text(formatPrice(item.subtotalVnd))
            }
        }
        HorizontalDivider()
        Text("Tạm tính: ${formatPrice(state.snapshot.totalVnd)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        state.request?.let {
            Text("Phí giao hàng: ${formatPrice(it.feeVnd)}")
            Text("Tổng thanh toán COD: ${formatPrice(it.totalVnd)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Text("Phí giao hàng demo 30.000 ₫; miễn phí từ 500.000 ₫. Thanh toán khi nhận hàng.")
        state.error?.let { CartErrorMessage(it) }
        state.orderError?.let { Text(orderErrorText(it), color = MaterialTheme.colorScheme.error) }
        Button(onPlace, enabled = !state.busy && state.request != null, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Đang đặt hàng…" else "Xác nhận đặt hàng COD")
        }
    }
}

@Composable
fun CartErrorMessage(error: CartError) {
    Text(cartErrorText(error), color = MaterialTheme.colorScheme.error)
}

fun cartErrorText(error: CartError): String = when (error) {
        CartError.UNAVAILABLE -> "Sản phẩm không còn được bán. Vui lòng kiểm tra giỏ hàng."
        CartError.STOCK -> "Số lượng vượt tồn kho hiện tại. Vui lòng điều chỉnh giỏ."
        CartError.QUANTITY -> "Số lượng phải từ 1 đến 99."
        CartError.PRICE_CHANGED -> "Giá sản phẩm đã thay đổi. Hãy xác nhận giá mới trong giỏ hàng."
        CartError.EMPTY -> "Giỏ hàng đang trống."
        CartError.SESSION -> "Phiên đăng nhập đã thay đổi. Vui lòng đăng nhập lại."
        CartError.STORAGE -> "Không thể đọc hoặc lưu giỏ hàng. Vui lòng thử lại."
        CartError.TOTAL_OVERFLOW -> "Tổng tiền vượt giới hạn xử lý. Vui lòng điều chỉnh giỏ."
    }

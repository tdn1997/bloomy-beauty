package com.example.bloomybeauty.feature.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloomybeauty.data.admin.*
import com.example.bloomybeauty.data.catalog.Product
import com.example.bloomybeauty.data.catalog.ProductSearch
import com.example.bloomybeauty.feature.home.formatPrice
import com.example.bloomybeauty.feature.home.productImage
import com.example.bloomybeauty.feature.order.OrderContents
import com.example.bloomybeauty.feature.order.OrderSummary
import com.example.bloomybeauty.feature.order.orderStatusLabel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

data class AdminUiState(val snapshot: AdminSnapshot? = null, val busy: Boolean = false, val error: AdminError? = null, val notice: String? = null)
class AdminViewModel(private val repository: AdminRepository, private val userId: Long) : ViewModel() {
    private val mutableState = MutableStateFlow(AdminUiState())
    val state = mutableState.asStateFlow()
    fun reload() = action { mutableState.value = mutableState.value.copy(snapshot = repository.load(userId)) }
    fun save(product: Product, isNew: Boolean, done: () -> Unit) = action {
        repository.save(userId, product, isNew)
        done()
        mutableState.value = mutableState.value.copy(snapshot = repository.load(userId), notice = "Đã lưu sản phẩm")
    }
    fun transition(id: Long, from: String, to: String) = action {
        repository.transition(userId,id,from,to)
        mutableState.value = mutableState.value.copy(snapshot = repository.load(userId), notice = "Đã cập nhật đơn hàng")
    }
    private fun action(block: suspend () -> Unit) {
        if(mutableState.value.busy) return
        mutableState.value = mutableState.value.copy(busy=true,error=null,notice=null)
        viewModelScope.launch {
            try { block() }
            catch(e: AdminException) {
                mutableState.value = mutableState.value.copy(error=e.error, snapshot=if(e.error==AdminError.PERMISSION) null else mutableState.value.snapshot)
            }
            catch(_: android.database.sqlite.SQLiteException) { mutableState.value = mutableState.value.copy(error=AdminError.STORAGE) }
            finally { mutableState.value = mutableState.value.copy(busy=false) }
        }
    }
    class Factory(private val repository: AdminRepository, private val userId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AdminViewModel(repository,userId) as T
    }
}
fun adminErrorText(error: AdminError): String = when(error) {
    AdminError.PERMISSION -> "Bạn không còn quyền quản trị. Vui lòng quay lại và đăng nhập lại."
    AdminError.INVALID_PRODUCT -> "Kiểm tra tên, thương hiệu, danh mục, dung tích, giá nguyên dương, tồn kho không âm và nội dung sản phẩm. Nguồn tham khảo phải là địa chỉ HTTPS hợp lệ."
    AdminError.CONFLICT -> "Dữ liệu đã thay đổi. Hãy tải lại và mở lại biểu mẫu trước khi lưu."
    AdminError.NOT_FOUND -> "Không tìm thấy dữ liệu. Hãy tải lại."
    AdminError.INVALID_TRANSITION -> "Không thể chuyển sang trạng thái này."
    AdminError.STOCK_OVERFLOW -> "Không thể hoàn tồn kho vì số lượng vượt giới hạn."
    AdminError.STORAGE -> "Không thể lưu dữ liệu. Vui lòng thử lại."
}

@Composable
fun AdminScreen(state: AdminUiState, onBack: () -> Unit, onReload: () -> Unit,
                onSave: (Product, Boolean, () -> Unit) -> Unit, onTransition: (Long,String,String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    var orderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable(stateSaver=TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    fun back() { if(editId!=null) editId=null else if(orderId!=null) orderId=null else onBack() }
    BackHandler { if(!state.busy) back() }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton({ back() },enabled=!state.busy) { Text("Quay lại") }
            Text("Quản trị",style=MaterialTheme.typography.titleLarge)
            TextButton(onReload,enabled=!state.busy) { Text("Tải lại") }
        }
        if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(adminErrorText(it),color=MaterialTheme.colorScheme.error) }
        state.notice?.let { Text(it) }
        val snapshot = state.snapshot ?: return@Column
        if(state.error==AdminError.PERMISSION) return@Column
        val edited = snapshot.products.find { it.id==editId }
        val order = snapshot.orders.find { it.id==orderId }
        if(editId!=null) {
            key(editId) { ProductEditor(edited, snapshot, state.busy, onSave) { editId=null } }
        } else if(order!=null) {
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(order.code, style=MaterialTheme.typography.titleLarge)
                OrderSummary(order); OrderContents(order)
                AdminRules.transitions[order.status].orEmpty().forEach { next ->
                    Button({ target=next },enabled=!state.busy,modifier=Modifier.fillMaxWidth()) { Text(if(next=="CANCELLED") "Hủy đơn hàng" else "Chuyển sang: ${orderStatusLabel(next)}") }
                }
                Text("Lịch sử xử lý",style=MaterialTheme.typography.titleMedium)
                if(snapshot.events[order.id].isNullOrEmpty()) Text("Đơn cũ chưa có lịch sử trước lần cập nhật này.")
                snapshot.events[order.id].orEmpty().forEach { event ->
                    Text("${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN")).format(event.time)} • ${event.actor}\n${event.from?.let { orderStatusLabel(it)+" → " }.orEmpty()}${orderStatusLabel(event.to)}")
                }
            }
            if(target!=null) AlertDialog(onDismissRequest={if(!state.busy) target=null},title={Text("Xác nhận cập nhật đơn")},
                text={Text("${order.code}: ${orderStatusLabel(order.status)} → ${orderStatusLabel(target!!)}")},
                confirmButton={TextButton({ onTransition(order.id,order.status,target!!);target=null },enabled=!state.busy) { Text("Xác nhận") }},
                dismissButton={TextButton({target=null}) {Text("Giữ nguyên")}})
        } else {
            Row { FilterChip(tab==0,{tab=0},label={Text("Sản phẩm")}); Spacer(Modifier.width(8.dp)); FilterChip(tab==1,{tab=1},label={Text("Đơn hàng")}) }
            if(tab==0) {
                OutlinedTextField(query,{query=it},label={Text("Tìm sản phẩm")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Button({editId="NEW"},enabled=!state.busy) {Text("Thêm sản phẩm")}
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    val products = snapshot.products.filter { ProductSearch.normalize(query.text) in ProductSearch.normalize("${it.name} ${it.brand}") }
                    if (products.isEmpty()) Text("Không tìm thấy sản phẩm. Hãy đổi từ khóa hoặc thêm sản phẩm mới.")
                    products.forEach { p ->
                        OutlinedCard(onClick={if(!state.busy) editId=p.id},modifier=Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                Image(painterResource(productImage(p.imageKey)),null,Modifier.size(64.dp))
                                Column(Modifier.weight(1f)) {Text(p.name);Text("${formatPrice(p.priceVnd)} • Tồn: ${p.stock}");Text(if(p.active) "Đang bán" else "Đã ẩn")}
                            }
                        }
                    }
                }
            } else {
                var expanded by remember { mutableStateOf(false) }
                Box { OutlinedButton({expanded=true}) {Text(filter?.let(::orderStatusLabel) ?: "Tất cả trạng thái")}
                    DropdownMenu(expanded,{expanded=false}) {
                        (listOf<String?>(null)+listOf("PENDING","CONFIRMED","SHIPPING","DELIVERED","CANCELLED")).forEach { value ->
                            DropdownMenuItem(text={Text(value?.let(::orderStatusLabel) ?: "Tất cả trạng thái")},onClick={filter=value;expanded=false})
                        }
                    }
                }
                val matches=snapshot.orders.filter {filter==null || it.status==filter}
                if(matches.isEmpty()) Text("Chưa có đơn hàng ở trạng thái này.")
                Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    matches.forEach { o -> OutlinedCard(onClick={if(!state.busy) orderId=o.id},modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp)) {Text(o.code);OrderSummary(o);Text(o.shipping.recipient)}} }
                }
            }
        }
    }
}

@Composable
private fun ProductEditor(original: Product?, snapshot: AdminSnapshot, busy: Boolean,
                          onSave: (Product,Boolean,()->Unit)->Unit, done: ()->Unit) {
    // Capture the opened revision; reloading the list must not silently make an old draft current.
    val base = remember { original ?: Product("", "", snapshot.categories.first().id,"",1,demoImageKeys.first(),"","","","") }
    val openedRevision = rememberSaveable { base.revision }
    val fields = listOf("Tên sản phẩm","Thương hiệu","Dung tích","Giá (VND)","Tồn kho","Mô tả","Thành phần","Cách dùng","Nguồn tham khảo (HTTPS, tùy chọn)")
    val initial = listOf(base.name,base.brand,base.volume,if(original==null) "" else base.priceVnd.toString(),base.stock.toString(),base.description,base.ingredients,base.usage,base.sourceUrl)
    val values = fields.indices.map { index -> rememberSaveable(stateSaver=TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial[index])) } }
    var category by rememberSaveable {mutableStateOf(base.categoryId)}
    var image by rememberSaveable {mutableStateOf(base.imageKey)}
    var active by rememberSaveable {mutableStateOf(base.active)}
    var invalid by rememberSaveable {mutableStateOf(false)}
    Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(if(original==null) "Thêm sản phẩm" else "Chỉnh sửa sản phẩm",style=MaterialTheme.typography.titleLarge)
        fields.forEachIndexed { i,label -> OutlinedTextField(values[i].value,{values[i].value=it;invalid=false},label={Text(label)},enabled=!busy,modifier=Modifier.fillMaxWidth(),singleLine=i<5 || i==8,
            keyboardOptions=KeyboardOptions(keyboardType=when(i) {3,4 -> KeyboardType.Number;8 -> KeyboardType.Uri;else -> KeyboardType.Text}, imeAction=if(i<5) ImeAction.Next else ImeAction.Default)) }
        Text("Danh mục")
        snapshot.categories.forEach { c -> FilterChip(category==c.id,{category=c.id},label={Text(c.name)},enabled=!busy) }
        Text("Ảnh sản phẩm có sẵn • Đang chọn ảnh ${demoImageKeys.indexOf(image)+1}")
        demoImageKeys.chunked(4).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) { row.forEach { key ->
            OutlinedButton({image=key},enabled=!busy,contentPadding=PaddingValues(2.dp),modifier=Modifier.weight(1f)) {Image(painterResource(productImage(key)),"Ảnh ${demoImageKeys.indexOf(key)+1}${if(image==key) ", đã chọn" else ""}",Modifier.size(52.dp))}
        } } }
        Row(verticalAlignment=Alignment.CenterVertically) {Switch(active,{active=it},enabled=!busy);Text(if(active) "Hiển thị để bán" else "Ẩn sản phẩm")}
        if(invalid) Text(adminErrorText(AdminError.INVALID_PRODUCT),color=MaterialTheme.colorScheme.error)
        Button({
            val price=values[3].value.text.toLongOrNull();val stock=values[4].value.text.toIntOrNull()
            val p=base.copy(revision=openedRevision,name=values[0].value.text,brand=values[1].value.text,volume=values[2].value.text,priceVnd=price?:-1,stock=stock?:-1,description=values[5].value.text,ingredients=values[6].value.text,usage=values[7].value.text,sourceUrl=values[8].value.text.trim(),categoryId=category,imageKey=image,active=active)
            if(!AdminRules.valid(p)) invalid=true else onSave(p,original==null,done)
        },enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("Lưu sản phẩm")}
    }
}

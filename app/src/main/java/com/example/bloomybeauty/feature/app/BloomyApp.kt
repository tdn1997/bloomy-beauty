package com.example.bloomybeauty.feature.app

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bloomybeauty.data.admin.AdminRepository
import com.example.bloomybeauty.data.cart.CartRepository
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.data.profile.ProfileRepository
import com.example.bloomybeauty.feature.admin.*
import com.example.bloomybeauty.feature.auth.*
import com.example.bloomybeauty.feature.cart.*
import com.example.bloomybeauty.feature.home.*
import com.example.bloomybeauty.feature.order.*
import com.example.bloomybeauty.feature.profile.*
import com.example.bloomybeauty.data.promotions.PromotionsRepository
import com.example.bloomybeauty.data.promotions.DisabledPromotionsRepository
import com.example.bloomybeauty.feature.promotions.PromotionsViewModel
import androidx.compose.ui.platform.LocalContext
import com.example.bloomybeauty.data.care.CareRoutineRepository
import com.example.bloomybeauty.feature.care.*
import com.example.bloomybeauty.events.registerOrderChanges

class ShopRepositories(val catalog: CatalogRepository, val cart: CartRepository, val orders: OrderRepository,
                       val profile: ProfileRepository, val admin: AdminRepository,
                       val promotions: PromotionsRepository = DisabledPromotionsRepository,
                       val care: CareRoutineRepository? = null,
                       val reviews: com.example.bloomybeauty.data.review.ReviewRepository? = null,
                       val addresses: com.example.bloomybeauty.data.address.AddressRepository? = null,
                       val statistics: com.example.bloomybeauty.data.statistics.StatisticsRepository? = null,
                       val pdfExporter: com.example.bloomybeauty.export.OrderPdfExporter? = null,
                       val inbox: com.example.bloomybeauty.data.notification.NotificationRepository? = null)
private enum class ShopScreen { HOME, CART, ORDERS, PROFILE, ADMIN, CARE, ADDRESSES, STATISTICS, INBOX }
data class InboxOpenRequest(val userId: Long,val notificationId: Long)
data class CareOpenRequest(val userId: Long, val routineId: Long)

@Composable
fun BloomyApp(auth: AuthViewModel, repositories: ShopRepositories, careOpenRequest: CareOpenRequest? = null, onCareOpenHandled: () -> Unit = {},
    inboxOpenRequest: InboxOpenRequest? = null,onInboxOpenHandled: () -> Unit = {}) {
    val authState by auth.state.collectAsStateWithLifecycle()
    val user = authState.user
    if (user == null) {
        LaunchedEffect(inboxOpenRequest) { if(inboxOpenRequest != null) onInboxOpenHandled() }
        AuthScreen(authState,auth::submit,auth::switchMode,auth::clearError,auth::logout)
        return
    }
    key(user.id) {
        var screenName by rememberSaveable { mutableStateOf(ShopScreen.HOME.name) }
        val screen = ShopScreen.valueOf(screenName)
        var careProductId by rememberSaveable { mutableStateOf<String?>(null) }
        val context = LocalContext.current.applicationContext
        LaunchedEffect(careOpenRequest) {
            if (careOpenRequest != null && repositories.care != null) {
                if (careOpenRequest.userId == user.id) { careProductId = null; screenName = ShopScreen.CARE.name }
                else {
                    android.widget.Toast.makeText(context, "Lịch không thuộc tài khoản hiện tại.", android.widget.Toast.LENGTH_LONG).show()
                    onCareOpenHandled()
                }
            }
        }
        val screens = rememberSaveableStateHolder()
        var openedOrderId by rememberSaveable {mutableStateOf<Long?>(null)}
        var addressReturnName by rememberSaveable { mutableStateOf(ShopScreen.PROFILE.name) }
        var addressRecipient by rememberSaveable { mutableStateOf<String?>(null) }
        var addressPhone by rememberSaveable { mutableStateOf<String?>(null) }
        var addressText by rememberSaveable { mutableStateOf<String?>(null) }
        fun openAddresses(from: ShopScreen, initial: com.example.bloomybeauty.data.cart.ShippingDetails? = null) {
            screens.removeState(ShopScreen.ADDRESSES.name)
            addressReturnName = from.name
            addressRecipient = initial?.recipient; addressPhone = initial?.phone; addressText = initial?.address
            screenName = ShopScreen.ADDRESSES.name
        }
        val home: HomeViewModel = viewModel(viewModelStoreOwner=auth,key="catalog-${user.id}",factory=HomeViewModel.Factory(repositories.catalog,user.id))
        val cart: CartViewModel = viewModel(viewModelStoreOwner=auth,key="cart-${user.id}",factory=CartViewModel.Factory(repositories.cart,user.id,user.name,repositories.orders))
        val orders: OrdersViewModel = viewModel(viewModelStoreOwner=auth,key="orders-${user.id}",factory=OrdersViewModel.Factory(repositories.orders,user.id))
        val profile: ProfileViewModel = viewModel(viewModelStoreOwner=auth,key="profile-${user.id}",factory=ProfileViewModel.Factory(repositories.profile,user.id))
        val admin: AdminViewModel = viewModel(viewModelStoreOwner=auth,key="admin-${user.id}",factory=AdminViewModel.Factory(repositories.admin,user.id))
        val statistics: com.example.bloomybeauty.feature.statistics.StatisticsViewModel? = repositories.statistics?.let {
            viewModel(viewModelStoreOwner=auth,key="statistics-${user.id}",factory=com.example.bloomybeauty.feature.statistics.StatisticsViewModel.Factory(it,user.id))
        }
        val inbox: com.example.bloomybeauty.feature.notification.NotificationViewModel? = repositories.inbox?.let {
            viewModel(viewModelStoreOwner=auth,key="inbox-${user.id}",factory=com.example.bloomybeauty.feature.notification.NotificationViewModel.Factory(it,user.id))
        }
        val inboxState = inbox?.state?.collectAsStateWithLifecycle()?.value
        fun openInboxOrder(id: Long) {
            screens.removeState(ShopScreen.ORDERS.name);openedOrderId=id;screenName=ShopScreen.ORDERS.name;orders.reload()
        }
        LaunchedEffect(inboxOpenRequest,inboxState?.busy) {
            val request = inboxOpenRequest ?: return@LaunchedEffect
            if(request.userId != user.id || inbox == null) {
                android.widget.Toast.makeText(context,"Thông báo không thuộc tài khoản hiện tại.",android.widget.Toast.LENGTH_LONG).show()
                onInboxOpenHandled()
            } else if(inboxState?.busy == false) {
                screenName=ShopScreen.INBOX.name
                inbox.open(request.notificationId,::openInboxOrder)
                onInboxOpenHandled()
            }
        }
        val cartState by cart.state.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val refreshAfterOrder by rememberUpdatedState(newValue = {
            home.reload(); cart.reload(); inbox?.reload()
            if (screen == ShopScreen.ORDERS) orders.reload()
            if (screen == ShopScreen.ADMIN) admin.reload()
            if (screen == ShopScreen.STATISTICS) statistics?.reload()
        })
        DisposableEffect(lifecycle, user.id) {
            var unregister: (() -> Unit)? = null
            fun start() {
                if (unregister == null) unregister = registerOrderChanges(context) { change ->
                    if (change.ownerUserId == user.id || user.role == com.example.bloomybeauty.data.auth.UserRole.ADMIN) refreshAfterOrder()
                }
            }
            fun stop() { unregister?.invoke(); unregister = null }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) start()
                if (event == Lifecycle.Event.ON_STOP) stop()
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
            onDispose { lifecycle.removeObserver(observer); stop() }
        }
        val refreshOnResume by rememberUpdatedState(newValue = {
            home.reload()
            cart.reload()
            inbox?.reload()
            when(screen) {
                ShopScreen.ORDERS -> orders.reload()
                ShopScreen.ADMIN -> admin.reload()
                ShopScreen.STATISTICS -> statistics?.reload()
                else -> Unit // Never overwrite a profile draft on resume.
            }
        })
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _,event -> if(event == Lifecycle.Event.ON_RESUME) refreshOnResume() }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        fun goHome() { screenName=ShopScreen.HOME.name;home.reload() }
        screens.SaveableStateProvider(screen.name) {
            when(screen) {
                ShopScreen.INBOX -> inbox?.let {
                    com.example.bloomybeauty.feature.notification.NotificationScreen(checkNotNull(inboxState),::goHome,it::reload,it::more,it::readAll) {id -> it.open(id,::openInboxOrder)}
                }
                ShopScreen.CARE -> repositories.care?.let { repository ->
                    val care: CareRoutineViewModel = viewModel(viewModelStoreOwner = auth, key = "care-${user.id}", factory = CareRoutineViewModel.Factory(repository, user.id))
                    CareScreen(care.state.collectAsStateWithLifecycle().value, home.state.collectAsStateWithLifecycle().value.products,
                        careProductId, careOpenRequest?.takeIf { it.userId == user.id }?.routineId, onCareOpenHandled,
                        ::goHome, care::reload, care::save, care::toggle, care::delete, care::clearError)
                }

                ShopScreen.STATISTICS -> {
                    if(user.role != com.example.bloomybeauty.data.auth.UserRole.ADMIN || statistics == null) {
                        androidx.compose.foundation.layout.Column {
                            androidx.compose.material3.Text("Chỉ quản trị viên mới xem được thống kê.")
                            androidx.compose.material3.TextButton({goHome()}) {androidx.compose.material3.Text("Quay lại")}
                        }
                    } else {
                        LaunchedEffect(Unit) {statistics.reload()}
                        com.example.bloomybeauty.feature.statistics.StatisticsScreen(statistics.state.collectAsStateWithLifecycle().value,
                            {screenName=ShopScreen.ADMIN.name;admin.reload()},statistics::reload,statistics::editFrom,statistics::editThrough,
                            statistics::apply,statistics::recent,statistics::allTime)
                    }
                }
                ShopScreen.ADDRESSES -> {
                    val repository = checkNotNull(repositories.addresses)
                    val addresses: com.example.bloomybeauty.feature.address.AddressViewModel = viewModel(viewModelStoreOwner=auth,
                        key="addresses-${user.id}", factory=com.example.bloomybeauty.feature.address.AddressViewModel.Factory(repository,user.id))
                    LaunchedEffect(Unit) { addresses.reload() }
                    com.example.bloomybeauty.feature.address.AddressScreen(addresses.state.collectAsStateWithLifecycle().value,
                        addressRecipient?.let { com.example.bloomybeauty.data.cart.ShippingDetails(it,addressPhone.orEmpty(),addressText.orEmpty()) },
                        {screenName = addressReturnName},addresses::reload,addresses::save,addresses::setDefault,addresses::delete,
                        onSelect = if(addressReturnName == ShopScreen.CART.name) { token -> addresses.select(token) { details ->
                            repositories.cart.saveShipping(user.id,details)
                            cart.editDraft(details); cart.reload(); screenName = ShopScreen.CART.name
                        } } else null)
                }
                ShopScreen.ADMIN -> AdminScreen(admin.state.collectAsStateWithLifecycle().value,
                    {goHome();cart.reload();orders.reload()},admin::reload,admin::save,admin::transition,
                    onOpenStatistics=statistics?.let { {screenName=ShopScreen.STATISTICS.name} },pdfExporter=repositories.pdfExporter,userId=user.id)
                ShopScreen.PROFILE -> ProfileScreen(profile.state.collectAsStateWithLifecycle().value,
                    ::goHome,profile::edit,{profile.save {updated -> auth.onProfileUpdated(updated);cart.updateUserName(updated.name);goHome()}},profile::reload, onOpenAddresses = repositories.addresses?.let { {openAddresses(ShopScreen.PROFILE)} })
                ShopScreen.ORDERS -> OrdersScreen(orders.state.collectAsStateWithLifecycle().value,::goHome,orders::reload,
                    {id -> orders.cancel(id) {home.reload();cart.reload()} }, reviews = repositories.reviews, userId = user.id,pdfExporter = repositories.pdfExporter,initialOrderId=openedOrderId)
                ShopScreen.CART -> CartScreen(cartState,
                    onBack={if(!cart.back()) goHome()},onQuantityChange=cart::setQuantity,onRemove=cart::remove,
                    onAcceptPrices=cart::acceptPrices,onContinue=cart::continueToShipping,onDraftChange=cart::editDraft,
                    onSaveShipping=cart::saveShipping,onRetry=cart::reload,onPlaceOrder=cart::placeOrder,
                    onFinishOrder={cart.finishOrder {screenName=ShopScreen.ORDERS.name;orders.reload();home.reload()}},
                    onOpenAddresses = repositories.addresses?.let { {openAddresses(ShopScreen.CART)} },
                    onSaveAddress = repositories.addresses?.let { {details -> openAddresses(ShopScreen.CART,details)} },
                    onCouponChange = cart::editCoupon,onApplyCoupon = cart::applyCoupon,onRemoveCoupon = cart::removeCoupon)
                ShopScreen.HOME -> {
                    val promotions: PromotionsViewModel = viewModel(viewModelStoreOwner=auth,
                        key="promotions-${user.id}",factory=PromotionsViewModel.Factory(repositories.promotions))
                    HomeScreen(user,home.state.collectAsStateWithLifecycle().value,
                    home::setQuery,home::setCategory,home::setSort,home::toggleFavorite,home::reload,auth::logout,
                    logoutBusy=authState.busy,logoutError=authState.error!=null,cartCount=cartState.snapshot.quantity,cartBusy=cartState.busy,
                    cartNotice=cartState.error?.let(::cartErrorText) ?: if(cartState.notice) "Đã thêm sản phẩm vào giỏ hàng" else null,
                    onNoticeShown=cart::clearNotice,onOpenCart={screenName=ShopScreen.CART.name;cart.open()},onAddToCart=cart::add,
                    onOpenOrders={openedOrderId=null;screens.removeState(ShopScreen.ORDERS.name);screenName=ShopScreen.ORDERS.name;orders.reload()},
                    onOpenAdmin={screenName=ShopScreen.ADMIN.name;admin.reload()},
                    onEditProfile={screenName=ShopScreen.PROFILE.name;profile.reload()},
                    onOpenCare=if (repositories.care != null) { productId -> screens.removeState(ShopScreen.CARE.name); careProductId = productId; screenName = ShopScreen.CARE.name } else null,
                    promotions=promotions.state.collectAsStateWithLifecycle().value,
                    onRetryPromotions=promotions::retry, reviews= repositories.reviews,
                    onOpenAddresses = repositories.addresses?.let { {openAddresses(ShopScreen.HOME)} },
                    onOpenInbox=inbox?.let {{screenName=ShopScreen.INBOX.name;it.reload()}},unreadNotifications=inboxState?.unread ?: 0)
                }
            }
        }
    }
}

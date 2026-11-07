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
                       val care: CareRoutineRepository? = null)
private enum class ShopScreen { HOME, CART, ORDERS, PROFILE, ADMIN, CARE }
data class CareOpenRequest(val userId: Long, val routineId: Long)

@Composable
fun BloomyApp(auth: AuthViewModel, repositories: ShopRepositories, careOpenRequest: CareOpenRequest? = null, onCareOpenHandled: () -> Unit = {}) {
    val authState by auth.state.collectAsStateWithLifecycle()
    val user = authState.user
    if (user == null) {
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
        val home: HomeViewModel = viewModel(viewModelStoreOwner=auth,key="catalog-${user.id}",factory=HomeViewModel.Factory(repositories.catalog,user.id))
        val cart: CartViewModel = viewModel(viewModelStoreOwner=auth,key="cart-${user.id}",factory=CartViewModel.Factory(repositories.cart,user.id,user.name,repositories.orders))
        val orders: OrdersViewModel = viewModel(viewModelStoreOwner=auth,key="orders-${user.id}",factory=OrdersViewModel.Factory(repositories.orders,user.id))
        val profile: ProfileViewModel = viewModel(viewModelStoreOwner=auth,key="profile-${user.id}",factory=ProfileViewModel.Factory(repositories.profile,user.id))
        val admin: AdminViewModel = viewModel(viewModelStoreOwner=auth,key="admin-${user.id}",factory=AdminViewModel.Factory(repositories.admin,user.id))
        val cartState by cart.state.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        val refreshAfterOrder by rememberUpdatedState(newValue = {
            home.reload(); cart.reload()
            if (screen == ShopScreen.ORDERS) orders.reload()
            if (screen == ShopScreen.ADMIN) admin.reload()
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
            when(screen) {
                ShopScreen.ORDERS -> orders.reload()
                ShopScreen.ADMIN -> admin.reload()
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
                ShopScreen.CARE -> repositories.care?.let { repository ->
                    val care: CareRoutineViewModel = viewModel(viewModelStoreOwner = auth, key = "care-${user.id}", factory = CareRoutineViewModel.Factory(repository, user.id))
                    CareScreen(care.state.collectAsStateWithLifecycle().value, home.state.collectAsStateWithLifecycle().value.products,
                        careProductId, careOpenRequest?.takeIf { it.userId == user.id }?.routineId, onCareOpenHandled,
                        ::goHome, care::reload, care::save, care::toggle, care::delete, care::clearError)
                }

                ShopScreen.ADMIN -> AdminScreen(admin.state.collectAsStateWithLifecycle().value,
                    {goHome();cart.reload();orders.reload()},admin::reload,admin::save,admin::transition)
                ShopScreen.PROFILE -> ProfileScreen(profile.state.collectAsStateWithLifecycle().value,
                    ::goHome,profile::edit,{profile.save {updated -> auth.onProfileUpdated(updated);cart.refreshDefaultAddress(updated.name);goHome()}},profile::reload)
                ShopScreen.ORDERS -> OrdersScreen(orders.state.collectAsStateWithLifecycle().value,::goHome,orders::reload,
                    {id -> orders.cancel(id) {home.reload();cart.reload()} })
                ShopScreen.CART -> CartScreen(cartState,
                    onBack={if(!cart.back()) goHome()},onQuantityChange=cart::setQuantity,onRemove=cart::remove,
                    onAcceptPrices=cart::acceptPrices,onContinue=cart::continueToShipping,onDraftChange=cart::editDraft,
                    onSaveShipping=cart::saveShipping,onRetry=cart::reload,onPlaceOrder=cart::placeOrder,
                    onFinishOrder={cart.finishOrder {screenName=ShopScreen.ORDERS.name;orders.reload();home.reload()}})
                ShopScreen.HOME -> {
                    val promotions: PromotionsViewModel = viewModel(viewModelStoreOwner=auth,
                        key="promotions-${user.id}",factory=PromotionsViewModel.Factory(repositories.promotions))
                    HomeScreen(user,home.state.collectAsStateWithLifecycle().value,
                    home::setQuery,home::setCategory,home::setSort,home::toggleFavorite,home::reload,auth::logout,
                    logoutBusy=authState.busy,logoutError=authState.error!=null,cartCount=cartState.snapshot.quantity,cartBusy=cartState.busy,
                    cartNotice=cartState.error?.let(::cartErrorText) ?: if(cartState.notice) "Đã thêm sản phẩm vào giỏ hàng" else null,
                    onNoticeShown=cart::clearNotice,onOpenCart={screenName=ShopScreen.CART.name;cart.open()},onAddToCart=cart::add,
                    onOpenOrders={screenName=ShopScreen.ORDERS.name;orders.reload()},
                    onOpenAdmin={screenName=ShopScreen.ADMIN.name;admin.reload()},
                    onEditProfile={screenName=ShopScreen.PROFILE.name;profile.reload()},
                    onOpenCare=if (repositories.care != null) { productId -> screens.removeState(ShopScreen.CARE.name); careProductId = productId; screenName = ShopScreen.CARE.name } else null,
                    promotions=promotions.state.collectAsStateWithLifecycle().value,
                    onRetryPromotions=promotions::retry)
                }
            }
        }
    }
}

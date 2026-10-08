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

class ShopRepositories(val catalog: CatalogRepository, val cart: CartRepository, val orders: OrderRepository,
                       val profile: ProfileRepository, val admin: AdminRepository)
private enum class ShopScreen { HOME, CART, ORDERS, PROFILE, ADMIN }

@Composable
fun BloomyApp(auth: AuthViewModel, repositories: ShopRepositories) {
    val authState by auth.state.collectAsStateWithLifecycle()
    val user = authState.user
    if (user == null) {
        AuthScreen(authState,auth::submit,auth::switchMode,auth::clearError,auth::logout)
        return
    }
    key(user.id) {
        var screenName by rememberSaveable { mutableStateOf(ShopScreen.HOME.name) }
        val screen = ShopScreen.valueOf(screenName)
        val screens = rememberSaveableStateHolder()
        val home: HomeViewModel = viewModel(viewModelStoreOwner=auth,key="catalog-${user.id}",factory=HomeViewModel.Factory(repositories.catalog,user.id))
        val cart: CartViewModel = viewModel(viewModelStoreOwner=auth,key="cart-${user.id}",factory=CartViewModel.Factory(repositories.cart,user.id,user.name,repositories.orders))
        val orders: OrdersViewModel = viewModel(viewModelStoreOwner=auth,key="orders-${user.id}",factory=OrdersViewModel.Factory(repositories.orders,user.id))
        val profile: ProfileViewModel = viewModel(viewModelStoreOwner=auth,key="profile-${user.id}",factory=ProfileViewModel.Factory(repositories.profile,user.id))
        val admin: AdminViewModel = viewModel(viewModelStoreOwner=auth,key="admin-${user.id}",factory=AdminViewModel.Factory(repositories.admin,user.id))
        val cartState by cart.state.collectAsStateWithLifecycle()
        val lifecycle = LocalLifecycleOwner.current.lifecycle
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
                ShopScreen.HOME -> HomeScreen(user,home.state.collectAsStateWithLifecycle().value,
                    home::setQuery,home::setCategory,home::setSort,home::toggleFavorite,home::reload,auth::logout,
                    logoutBusy=authState.busy,logoutError=authState.error!=null,cartCount=cartState.snapshot.quantity,cartBusy=cartState.busy,
                    cartNotice=cartState.error?.let(::cartErrorText) ?: if(cartState.notice) "Đã thêm sản phẩm vào giỏ hàng" else null,
                    onNoticeShown=cart::clearNotice,onOpenCart={screenName=ShopScreen.CART.name;cart.open()},onAddToCart=cart::add,
                    onOpenOrders={screenName=ShopScreen.ORDERS.name;orders.reload()},
                    onOpenAdmin={screenName=ShopScreen.ADMIN.name;admin.reload()},
                    onEditProfile={screenName=ShopScreen.PROFILE.name;profile.reload()})
            }
        }
    }
}

package com.example.bloomybeauty.feature.home

import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bloomybeauty.R
import com.example.bloomybeauty.data.auth.AuthUser
import com.example.bloomybeauty.data.catalog.Product
import com.example.bloomybeauty.data.catalog.ProductSearch
import com.example.bloomybeauty.data.catalog.ProductSort

private enum class HomeTab(val label: Int) {
    HOME(R.string.nav_home), EXPLORE(R.string.nav_explore), FAVORITES(R.string.nav_favorites), ACCOUNT(R.string.nav_account),
}

@Composable
fun HomeScreen(
    user: AuthUser,
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onSortChange: (ProductSort) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    logoutBusy: Boolean = false,
    logoutError: Boolean = false,
    cartCount: Int = 0,
    cartBusy: Boolean = false,
    cartNotice: String? = null,
    onNoticeShown: () -> Unit = {},
    onOpenCart: () -> Unit = {},
    onAddToCart: (String) -> Unit = {},
    onOpenOrders: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onOpenAdmin: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(cartNotice) {
        if (cartNotice != null) {
            snackbar.showSnackbar(cartNotice)
            onNoticeShown()
        }
    }
    var tabName by rememberSaveable(user.id) { mutableStateOf(HomeTab.HOME.name) }
    var selectedProductId by rememberSaveable(user.id) { mutableStateOf<String?>(null) }
    val tab = HomeTab.valueOf(tabName)
    val selectedProduct = state.products.firstOrNull { it.id == selectedProductId }
    BackHandler(selectedProductId != null || tab != HomeTab.HOME) {
        if (selectedProductId != null) selectedProductId = null else tabName = HomeTab.HOME.name
    }
    if (selectedProduct != null) {
        ProductDetail(
            selectedProduct, selectedProduct.id in state.favoriteIds, state.savingFavorite,
            state.error, { selectedProductId = null }, { onToggleFavorite(selectedProduct.id) },
            snackbar, cartCount, cartBusy, onOpenCart, { onAddToCart(selectedProduct.id) },
        )
        return
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                HomeTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tabName = item.name },
                        icon = {
                            val icon = when (item) {
                                HomeTab.HOME -> Icons.Outlined.Home
                                HomeTab.EXPLORE -> Icons.Outlined.Search
                                HomeTab.FAVORITES -> Icons.Outlined.FavoriteBorder
                                HomeTab.ACCOUNT -> Icons.Outlined.Person
                            }
                            BadgedBox(badge = {
                                if (item == HomeTab.FAVORITES && state.favoriteIds.isNotEmpty()) {
                                    Badge { Text(state.favoriteIds.size.toString()) }
                                }
                            }) { Icon(icon, contentDescription = null) }
                        },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { padding ->
        if (tab == HomeTab.ACCOUNT) {
            AccountPage(user, logoutBusy, logoutError, onLogout, onOpenOrders, onEditProfile, onOpenAdmin, Modifier.padding(padding))
        } else {
            val products = ProductSearch.filter(
                state.products, state.query, state.categoryId, state.sort,
                tab == HomeTab.FAVORITES, state.favoriteIds,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize().padding(padding).imePadding().testTag("product_grid"),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    StoreHeader(user.name, cartCount, onOpenCart) { tabName = HomeTab.ACCOUNT.name }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchField(state.query, onQueryChange)
                }
                if (tab == HomeTab.HOME && state.query.isBlank() && state.categoryId == null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        CollectionBanner { onCategoryChange("skin"); tabName = HomeTab.EXPLORE.name }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Text(stringResource(R.string.shop_by_category), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(state.categoryId == null, { onCategoryChange(null) }, { Text(stringResource(R.string.all_categories)) })
                            state.categories.forEach { category ->
                                FilterChip(state.categoryId == category.id, { onCategoryChange(category.id) }, { Text(category.name) })
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(when (tab) {
                                    HomeTab.FAVORITES -> R.string.favorite_products
                                    HomeTab.EXPLORE -> R.string.explore_products
                                    else -> R.string.curated_products
                                }),
                                modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            )
                            Text(stringResource(R.string.product_count, products.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProductSort.entries.forEach { sort ->
                                FilterChip(state.sort == sort, { onSortChange(sort) }, { Text(stringResource(sortLabel(sort))) })
                            }
                        }
                    }
                }
                if (state.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                } else {
                    if (state.error) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(stringResource(R.string.catalog_error), color = MaterialTheme.colorScheme.error)
                                TextButton(onRetry) { Text(stringResource(R.string.retry_action)) }
                            }
                        }
                    }
                    if (products.isEmpty() && !state.error) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyProducts(tab == HomeTab.FAVORITES) { onQueryChange(""); onCategoryChange(null) }
                        }
                    }
                    items(products, key = { it.id }) { product ->
                        ProductCard(
                            product, product.id in state.favoriteIds, !state.savingFavorite,
                            { selectedProductId = product.id }, { onToggleFavorite(product.id) },
                        )
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.reference_prices_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StoreHeader(name: String, cartCount: Int, onCart: () -> Unit, onAccount: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.welcome_user, name), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        CartButton(cartCount, onCart)
        IconButton(onAccount, Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)) {
            Icon(Icons.Outlined.Person, stringResource(R.string.nav_account), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun CartButton(count: Int, onCart: () -> Unit) {
    IconButton(onCart) {
        BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
            Icon(Icons.Outlined.ShoppingCart, "Mở giỏ hàng")
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(query)) }
    // Reset only for an external action (e.g. clearing filters), never during IME composition.
    LaunchedEffect(query) {
        if (value.text != query) value = TextFieldValue(query)
    }
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value, { value = it; onQueryChange(it.text) }, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_products_hint)) }, singleLine = true,
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (value.text.isNotEmpty()) IconButton({ value = TextFieldValue(""); onQueryChange("") }) {
                Icon(Icons.Outlined.Close, stringResource(R.string.clear_search))
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }), shape = RoundedCornerShape(18.dp),
    )
}

@Composable
private fun CollectionBanner(onExplore: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF2EADF)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.collection_eyebrow), style = MaterialTheme.typography.labelMedium, color = Color(0xFF6A5945))
                Text(stringResource(R.string.collection_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF342D25))
                Text(stringResource(R.string.collection_description), style = MaterialTheme.typography.bodySmall, color = Color(0xFF6A5945))
                Button(onExplore, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.collection_action)) }
            }
            Spacer(Modifier.width(8.dp))
            Image(painterResource(R.drawable.product_cleanser), null, Modifier.weight(1f).aspectRatio(0.8f).clip(RoundedCornerShape(18.dp)))
        }
    }
}

@Composable
private fun ProductCard(product: Product, favorite: Boolean, favoriteEnabled: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.clickable(onClick = onOpen)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White)) {
                Image(painterResource(productImage(product.imageKey)), product.name, Modifier.fillMaxSize().padding(8.dp))
                IconButton(onFavorite, enabled = favoriteEnabled, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.White.copy(alpha = 0.9f), CircleShape)) {
                    FavoriteIcon(favorite, product.name)
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(product.brand.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(product.name, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(product.volume, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatPrice(product.priceVnd), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FavoriteIcon(favorite: Boolean, name: String) {
    Icon(
        if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
        stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite, name),
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EmptyProducts(favorites: Boolean, onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(if (favorites) Icons.Outlined.FavoriteBorder else Icons.Outlined.Search, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(if (favorites) R.string.empty_favorites else R.string.empty_products), style = MaterialTheme.typography.titleMedium)
        TextButton(onReset) { Text(stringResource(R.string.reset_filters)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductDetail(product: Product, favorite: Boolean, busy: Boolean, error: Boolean, onBack: () -> Unit, onFavorite: () -> Unit,
                          snackbar: SnackbarHostState, cartCount: Int, cartBusy: Boolean, onCart: () -> Unit, onAdd: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.product_detail_title)) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back_action)) } },
                actions = {
                    CartButton(cartCount, onCart)
                    IconButton(onFavorite, enabled = !busy) { FavoriteIcon(favorite, product.name) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Image(
                painterResource(productImage(product.imageKey)), product.name,
                Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(24.dp)).background(Color.White),
            )
            Text("${product.brand} · ${product.volume}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(product.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(formatPrice(product.priceVnd), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.reference_prices_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (!product.active) "Đã ngừng bán" else if (product.stock == 0) "Hết hàng" else "Tồn kho demo: ${product.stock}",
                style = MaterialTheme.typography.bodyMedium)
            Button(onAdd, enabled = product.active && product.stock > 0 && !cartBusy, modifier = Modifier.fillMaxWidth()) {
                Text(if (cartBusy) "Đang xử lý…" else "Thêm vào giỏ")
            }
            if (error) Text(stringResource(R.string.catalog_error), color = MaterialTheme.colorScheme.error)
            HorizontalDivider()
            DetailSection(R.string.product_description, product.description)
            DetailSection(R.string.product_ingredients, product.ingredients)
            DetailSection(R.string.product_usage, product.usage)
            Button(onFavorite, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (favorite) R.string.favorite_saved else R.string.favorite_save_action))
            }
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(product.sourceUrl))
                    try {
                        context.startActivity(intent)
                    } catch (exception: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.no_browser, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.official_product_page)) }
        }
    }
}

@Composable
private fun DetailSection(title: Int, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AccountPage(user: AuthUser, busy: Boolean, error: Boolean, onLogout: () -> Unit, onOrders: () -> Unit, onEdit: () -> Unit, onAdmin: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(80.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Person, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(stringResource(R.string.nav_account), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), tonalElevation = 1.dp) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.full_name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(user.name, style = MaterialTheme.typography.titleMedium)
                HorizontalDivider()
                Text(stringResource(R.string.email), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(user.email, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (user.role == com.example.bloomybeauty.data.auth.UserRole.ADMIN) OutlinedButton(onAdmin, modifier = Modifier.fillMaxWidth()) { Text("Quản trị cửa hàng") }
        OutlinedButton(onOrders, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) { Text("Đơn hàng của tôi") }
        OutlinedButton(onEdit, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) { Text("Chỉnh sửa hồ sơ") }
        if (error) Text(stringResource(R.string.error_storage), color = MaterialTheme.colorScheme.error)
        Button(onLogout, enabled = !busy, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Text(stringResource(if (busy) R.string.logging_out else R.string.logout_action))
        }
    }
}

private fun sortLabel(sort: ProductSort): Int = when (sort) {
    ProductSort.FEATURED -> R.string.sort_featured
    ProductSort.PRICE_ASCENDING -> R.string.sort_price_low
    ProductSort.PRICE_DESCENDING -> R.string.sort_price_high
}

package com.example.bloomybeauty.feature.cart

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CartStep { ITEMS, SHIPPING, REVIEW, SUCCESS }
data class CartUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val snapshot: CartSnapshot = CartSnapshot(),
    val draft: ShippingDetails = ShippingDetails(),
    val step: CartStep = CartStep.ITEMS,
    val error: CartError? = null,
    val shippingError: ShippingError? = null,
    val notice: Boolean = false,
    val request: CheckoutRequest? = null,
    val placedOrder: CustomerOrder? = null,
    val orderError: OrderError? = null,
)

class CartViewModel(private val repository: CartRepository, private val userId: Long, private var userName: String, private val orders: OrderRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CartUiState())
    val state = mutableState.asStateFlow()
    private var draftLoaded = false
    private var reloadPending = false

    init { reload() }

    fun refreshDefaultAddress(name: String) {
        userName = name
        draftLoaded = false
        reload()
    }

    fun open() {
        mutableState.update { it.copy(step = CartStep.ITEMS, error = null, notice = false) }
        reload()
    }

    fun reload() {
        if (state.value.busy) { reloadPending = true; return }
        loadCurrent()
    }

    private fun loadCurrent() = runAction {
        val snapshot = repository.load(userId)
        mutableState.update {
            val draft = if (draftLoaded) it.draft else snapshot.shipping.let { saved ->
                if (saved.recipient.isBlank()) saved.copy(recipient = userName) else saved
            }
            it.copy(snapshot = snapshot, draft = draft)
        }
        draftLoaded = true
        val recovery = orders.recover(userId)
        if (recovery?.order?.status == "CANCELLED") {
            orders.acknowledge(userId, recovery.request.id)
            mutableState.update { it.copy(request = null, placedOrder = null, step = CartStep.ITEMS) }
        } else if (recovery?.order != null) {
            mutableState.update { it.copy(request = recovery.request, placedOrder = recovery.order, step = CartStep.SUCCESS) }
        } else {
            val matches = recovery != null && snapshot.canContinue &&
                recovery.request.lines.associate { it.productId to (it.quantity to it.unitPriceVnd) } ==
                snapshot.items.associate { it.product.id to (it.quantity to it.product.priceVnd) } && recovery.request.shipping == snapshot.shipping
            if (state.value.step == CartStep.REVIEW) {
                repository.checkCart(snapshot)
                if (!matches) {
                    mutableState.update { it.copy(request = null, placedOrder = null, step = CartStep.ITEMS, orderError = OrderError.CART_CHANGED) }
                }
            } else if (matches && state.value.step == CartStep.ITEMS) {
                mutableState.update { it.copy(request = recovery!!.request, step = CartStep.REVIEW) }
            }
        }
    }

    fun add(productId: String) = runAction {
        repository.add(userId, productId)
        val snapshot = repository.load(userId)
        mutableState.update { it.copy(snapshot = snapshot, notice = true) }
    }

    fun setQuantity(productId: String, quantity: Int) = mutate { repository.setQuantity(userId, productId, quantity) }
    fun remove(productId: String) = mutate { repository.remove(userId, productId) }
    fun acceptPrices() {
        val prices = state.value.snapshot.items.associate { it.product.id to it.product.priceVnd }
        mutate { repository.acceptCurrentPrices(userId, prices) }
    }

    fun continueToShipping() = runAction {
        val snapshot = repository.validateForShipping(userId)
        mutableState.update { it.copy(snapshot = snapshot, step = CartStep.SHIPPING) }
    }

    fun saveShipping() = runAction {
        val snapshot = repository.saveShipping(userId, state.value.draft)
        val request = orders.prepare(userId)
        val recovery = orders.recover(userId)
        mutableState.update { it.copy(snapshot = snapshot, draft = snapshot.shipping, request = request,
            placedOrder = recovery?.order, step = if (recovery?.order != null) CartStep.SUCCESS else CartStep.REVIEW) }
    }

    fun placeOrder() = runAction {
        val request = state.value.request ?: throw OrderException(OrderError.INVALID_REQUEST)
        val order = orders.create(userId, request)
        mutableState.update { it.copy(placedOrder = order, step = CartStep.SUCCESS) }
        // The commit is already confirmed even if refreshing the cart subsequently fails.
        val snapshot = repository.load(userId)
        mutableState.update { it.copy(snapshot = snapshot) }
    }

    fun finishOrder(onDone: () -> Unit) = runAction {
        state.value.request?.let { orders.acknowledge(userId, it.id) }
        mutableState.update { it.copy(request = null, placedOrder = null, step = CartStep.ITEMS) }
        onDone()
    }

    fun editDraft(details: ShippingDetails) { mutableState.update { it.copy(draft = details, shippingError = null) } }
    fun back(): Boolean {
        if (state.value.busy) return true
        return when (state.value.step) {
            CartStep.SUCCESS -> { finishOrder {}; true }
            CartStep.REVIEW -> { mutableState.update { it.copy(step = CartStep.SHIPPING) }; true }
            CartStep.SHIPPING -> { mutableState.update { it.copy(step = CartStep.ITEMS) }; true }
            CartStep.ITEMS -> false
        }
    }
    fun clearNotice() { mutableState.update { it.copy(notice = false, error = null) } }

    private fun mutate(block: suspend () -> Unit) = runAction {
        block()
        val snapshot = repository.load(userId)
        mutableState.update { it.copy(snapshot = snapshot) }
    }

    private fun runAction(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null, shippingError = null, notice = false, orderError = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (exception: CartException) {
                mutableState.update { it.copy(error = exception.error, step = CartStep.ITEMS) }
                refreshAfterError()
            } catch (exception: ShippingException) {
                mutableState.update { it.copy(shippingError = exception.error) }
            } catch (exception: OrderException) {
                mutableState.update { it.copy(orderError = exception.error, step = if (exception.error == OrderError.CART_CHANGED) CartStep.ITEMS else it.step) }
                refreshAfterError()
            } catch (exception: SQLiteException) {
                mutableState.update {
                    if (it.step == CartStep.REVIEW && it.request != null) it.copy(orderError = OrderError.STORAGE)
                    else it.copy(error = CartError.STORAGE)
                }
            } catch (exception: ArithmeticException) {
                mutableState.update { it.copy(error = CartError.TOTAL_OVERFLOW) }
            } finally {
                mutableState.update { it.copy(busy = false, loading = false) }
                if (reloadPending) { reloadPending = false; reload() }
            }
        }
    }

    private suspend fun refreshAfterError() {
        try {
            val snapshot = repository.load(userId)
            mutableState.update { it.copy(snapshot = snapshot) }
        } catch (exception: CartException) {
            mutableState.update { it.copy(error = exception.error, snapshot = CartSnapshot(), draft = ShippingDetails(),
                request = null, placedOrder = null, step = CartStep.ITEMS) }
        } catch (exception: SQLiteException) {
            mutableState.update { it.copy(error = CartError.STORAGE) }
        }
    }

    class Factory(private val repository: CartRepository, private val userId: Long, private val name: String, private val orders: OrderRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CartViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return CartViewModel(repository, userId, name, orders) as T
        }
    }
}

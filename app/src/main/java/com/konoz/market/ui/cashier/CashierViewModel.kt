package com.konoz.market.ui.cashier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.core.Money
import com.konoz.market.core.Quantities
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.PaymentMethod
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.domain.Cart
import com.konoz.market.domain.CashierMessage
import com.konoz.market.domain.CashierSession
import com.konoz.market.domain.CheckoutOutcome
import com.konoz.market.domain.PaymentInput
import com.konoz.market.domain.SaleResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CashierViewModel(
    private val session: CashierSession,
    db: KonozDatabase,
) : ViewModel() {

    val cart: StateFlow<Cart> = session.cart
    val message: StateFlow<CashierMessage?> = session.message
    val canOverridePrice: StateFlow<Boolean> = session.canOverridePrice

    /** Held invoices, excluding the cart that is currently open (its auto-saved draft). */
    val held: StateFlow<List<SalesInvoice>> = combine(db.sales().observeHeld(), session.cart) { list, c ->
        list.filter { it.uuid != c.uuid }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favorites: StateFlow<List<Product>> = db.products().observeFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<Product>> = _query
        .debounce(150)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else db.products().search(q.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _scanning = MutableStateFlow(true)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _lastSale = MutableStateFlow<SaleResult?>(null)
    val lastSale: StateFlow<SaleResult?> = _lastSale.asStateFlow()

    init {
        viewModelScope.launch { session.start() }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setScanning(on: Boolean) {
        _scanning.value = on
    }

    fun onScan(code: String) {
        viewModelScope.launch { session.scan(code) }
    }

    fun addProduct(productId: Long) {
        viewModelScope.launch {
            session.addProduct(productId)
            _query.value = ""
        }
    }

    fun changeQty(productId: Long, deltaUnits: Int) {
        viewModelScope.launch { session.changeQty(productId, deltaUnits) }
    }

    fun remove(productId: Long) {
        viewModelScope.launch { session.remove(productId) }
    }

    /** Applies the edit dialog: quantity, then price (admin only), then discount, so clamping works on the final values. */
    fun applyLineEdit(productId: Long, qtyText: String, priceText: String, discountText: String) {
        viewModelScope.launch {
            val qty = Quantities.parseMilli(qtyText)
            val overridePrice = canOverridePrice.value
            val price = if (overridePrice) Money.parseToMinor(priceText) else null
            val discount = Money.parseToMinor(discountText.ifBlank { "0" })
            if (qty == null || discount == null || (overridePrice && price == null)) {
                session.postMessage(CashierMessage.InvalidInput)
                return@launch
            }
            session.setQty(productId, qty)
            if (price != null) session.setPrice(productId, price)
            session.setLineDiscount(productId, discount)
        }
    }

    fun setInvoiceDiscount(text: String) {
        viewModelScope.launch {
            val d = Money.parseToMinor(text.ifBlank { "0" })
            if (d == null) session.postMessage(CashierMessage.InvalidInput) else session.setInvoiceDiscount(d)
        }
    }

    fun hold() {
        viewModelScope.launch { session.hold() }
    }

    fun resume(invoiceId: Long) {
        viewModelScope.launch { session.resume(invoiceId) }
    }

    fun discardHeld(invoiceId: Long) {
        viewModelScope.launch { session.discardHeld(invoiceId) }
    }

    fun pay(cashText: String, cardText: String, walletText: String) {
        viewModelScope.launch {
            val cash = Money.parseToMinor(cashText.ifBlank { "0" })
            val card = Money.parseToMinor(cardText.ifBlank { "0" })
            val wallet = Money.parseToMinor(walletText.ifBlank { "0" })
            if (cash == null || card == null || wallet == null) {
                session.postMessage(CashierMessage.InvalidInput)
                return@launch
            }
            val payments = listOf(
                PaymentInput(PaymentMethod.CASH, cash),
                PaymentInput(PaymentMethod.CARD, card),
                PaymentInput(PaymentMethod.WALLET, wallet),
            ).filter { it.amount > 0L }
            handle(session.checkout(payments))
        }
    }

    fun confirmExpiredSale() {
        viewModelScope.launch { session.confirmExpiredSale()?.let { handle(it) } }
    }

    fun consumeMessage() = session.consumeMessage()

    fun dismissSale() {
        _lastSale.value = null
    }

    private fun handle(outcome: CheckoutOutcome) {
        if (outcome is CheckoutOutcome.Success) _lastSale.value = outcome.result
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as KonozApp
                CashierViewModel(
                    CashierSession(app.database, app.productRepository, app.saleService, app::activeUserId),
                    app.database,
                )
            }
        }
    }
}

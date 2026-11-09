package com.konoz.market.domain

import com.konoz.market.core.Money
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AppSetting
import com.konoz.market.data.entity.AuditLog
import com.konoz.market.data.entity.InvoiceStatus
import com.konoz.market.data.entity.Product
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import com.konoz.market.data.repo.ProductRepository
import com.konoz.market.data.seed.SettingKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

sealed interface CashierMessage {
    data class UnknownBarcode(val code: String) : CashierMessage
    data class ExpiredWarning(val productName: String) : CashierMessage
    data class ProductInactive(val productName: String) : CashierMessage
    data class PaymentShort(val total: Long, val paid: Long) : CashierMessage
    data class OutOfStock(val productName: String) : CashierMessage
    data object ConfirmExpiredSale : CashierMessage
    data object NotAllowed : CashierMessage
    data object InvalidInput : CashierMessage
    data object EmptyCart : CashierMessage
    data object CardOverpay : CashierMessage
    data object SaleFailed : CashierMessage
}

sealed interface CheckoutOutcome {
    data class Success(val result: SaleResult) : CheckoutOutcome
    data object Failed : CheckoutOutcome
}

/**
 * The cashier's working state: the current cart plus everything that touches the database.
 *
 *  - Every change to a non-empty cart is auto-saved as a HELD invoice (same uuid), so an abrupt app
 *    close never loses the sale. [start] restores it.
 *  - Operations are serialized with a mutex so two quick scans can never race.
 *  - Cashiers (non-admin) cannot override prices and are limited to cashier_max_discount_bp.
 */
class CashierSession(
    private val db: KonozDatabase,
    private val products: ProductRepository,
    private val sales: SaleService,
    private val userIdProvider: suspend () -> Long,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = Mutex()
    private lateinit var user: User
    private var taxBasisPoints = 0
    private var maxDiscountBp = 1000
    private var allowNegativeStock = true
    private var pendingPayments: List<PaymentInput>? = null

    private val _cart = MutableStateFlow(Cart(newUuid()))
    val cart: StateFlow<Cart> = _cart.asStateFlow()

    private val _message = MutableStateFlow<CashierMessage?>(null)
    val message: StateFlow<CashierMessage?> = _message.asStateFlow()

    private val _canOverridePrice = MutableStateFlow(false)
    val canOverridePrice: StateFlow<Boolean> = _canOverridePrice.asStateFlow()

    fun postMessage(m: CashierMessage) {
        _message.value = m
    }

    fun consumeMessage() {
        _message.value = null
    }

    suspend fun start() {
        lock.withLock {
            user = db.users().getById(userIdProvider()) ?: error("no active user")
            _canOverridePrice.value = isAdmin()
            val s = db.settings()
            taxBasisPoints = if (s.get(SettingKeys.TAX_ENABLED) == "true") s.get(SettingKeys.TAX_BASIS_POINTS)?.toIntOrNull() ?: 0 else 0
            maxDiscountBp = s.get(SettingKeys.CASHIER_MAX_DISCOUNT_BP)?.toIntOrNull() ?: 1000
            allowNegativeStock = s.get(SettingKeys.ALLOW_NEGATIVE_STOCK) != "false"

            // Restore the cart that was open when the app last stopped.
            val uuid = s.get(CURRENT_CART_KEY)
            val held = uuid?.let { db.sales().getByUuid(it) }
            if (held != null && held.status == InvoiceStatus.HELD) {
                val draft = sales.resumeInvoice(held.id)
                if (draft != null) _cart.value = restore(draft)
            } else {
                _cart.value = _cart.value.copy(taxBasisPoints = taxBasisPoints)
            }
        }
    }

    // ---------- adding ----------
    suspend fun scan(code: String) {
        lock.withLock {
            val trimmed = code.trim()
            val found = products.lookupBarcode(trimmed)
            if (found == null) {
                _message.value = CashierMessage.UnknownBarcode(trimmed)
                return@withLock
            }
            addLocked(found.first, found.second, trimmed)
        }
    }

    suspend fun addProduct(productId: Long) {
        lock.withLock {
            val p = db.products().getById(productId) ?: return@withLock
            addLocked(p, 1, null)
        }
    }

    private suspend fun addLocked(p: Product, units: Int, barcode: String?) {
        if (!p.isActive) {
            _message.value = CashierMessage.ProductInactive(p.name)
            return
        }
        if (hasOnlyExpiredStock(p.id)) _message.value = CashierMessage.ExpiredWarning(p.name)
        _cart.value = _cart.value.add(p, units, barcode)
        persist()
    }

    private suspend fun hasOnlyExpiredStock(productId: Long): Boolean {
        val now = clock()
        val batches = db.stock().fefoBatches(productId)
        return batches.isNotEmpty() && batches.all { it.expiryDate != null && it.expiryDate <= now }
    }

    // ---------- editing ----------
    suspend fun setQty(productId: Long, qtyMilli: Long) {
        lock.withLock {
            val item = _cart.value.items.firstOrNull { it.productId == productId } ?: return@withLock
            if (qtyMilli > 0L && !item.unit.isFractional && qtyMilli % 1000L != 0L) {
                _message.value = CashierMessage.InvalidInput
                return@withLock
            }
            _cart.value = _cart.value.setQty(productId, qtyMilli)
            persist()
        }
    }

    suspend fun changeQty(productId: Long, deltaUnits: Int) {
        val current = _cart.value.items.firstOrNull { it.productId == productId } ?: return
        setQty(productId, current.qtyMilli + deltaUnits * 1000L)
    }

    suspend fun remove(productId: Long) {
        lock.withLock {
            _cart.value = _cart.value.remove(productId)
            persist()
        }
    }

    /** Admin only. The change is written to the audit log. */
    suspend fun setPrice(productId: Long, price: Long) {
        lock.withLock {
            val item = _cart.value.items.firstOrNull { it.productId == productId } ?: return@withLock
            if (!isAdmin()) {
                _message.value = CashierMessage.NotAllowed
                return@withLock
            }
            if (price < 0L) {
                _message.value = CashierMessage.InvalidInput
                return@withLock
            }
            if (price == item.unitPrice) return@withLock
            _cart.value = _cart.value.setUnitPrice(productId, price)
            db.audit().insert(
                AuditLog(
                    userId = user.id, action = "PRICE_OVERRIDE", entity = "product", entityId = productId,
                    details = "${item.unitPrice} -> $price", createdAt = clock(),
                )
            )
            persist()
        }
    }

    suspend fun setLineDiscount(productId: Long, discount: Long) {
        lock.withLock {
            val item = _cart.value.items.firstOrNull { it.productId == productId } ?: return@withLock
            if (discount < 0L) {
                _message.value = CashierMessage.InvalidInput
                return@withLock
            }
            if (!isAdmin() && discount > Money.percentOf(item.gross, maxDiscountBp)) {
                _message.value = CashierMessage.NotAllowed
                return@withLock
            }
            _cart.value = _cart.value.setLineDiscount(productId, discount)
            persist()
        }
    }

    suspend fun setInvoiceDiscount(discount: Long) {
        lock.withLock {
            if (discount < 0L) {
                _message.value = CashierMessage.InvalidInput
                return@withLock
            }
            val afterItems = _cart.value.items.sumOf { it.net }
            if (!isAdmin() && discount > Money.percentOf(afterItems, maxDiscountBp)) {
                _message.value = CashierMessage.NotAllowed
                return@withLock
            }
            _cart.value = _cart.value.setInvoiceDiscount(discount)
            persist()
        }
    }

    // ---------- hold / resume ----------
    /** Parks the current cart (it is already saved) and starts a fresh one. Returns the held invoice id. */
    suspend fun hold(): Long? = lock.withLock {
        val old = _cart.value
        if (old.isEmpty) return@withLock null
        persist()
        val id = db.sales().getByUuid(old.uuid)?.id
        startNewCart()
        id
    }

    suspend fun resume(invoiceId: Long): Boolean = lock.withLock {
        val draft = sales.resumeInvoice(invoiceId) ?: return@withLock false
        _cart.value = restore(draft)
        upsertCurrent()
        true
    }

    suspend fun discardHeld(invoiceId: Long) {
        lock.withLock { sales.discardHeld(invoiceId) }
    }

    /** Abandons the current cart and its auto-saved draft. */
    suspend fun clear() {
        lock.withLock {
            deleteDraftRow(_cart.value.uuid)
            startNewCart()
        }
    }

    // ---------- checkout ----------
    suspend fun checkout(payments: List<PaymentInput>): CheckoutOutcome =
        lock.withLock { completeLocked(payments, allowExpired = false) }

    /** Retry the last checkout after the cashier accepted the expired-product warning. */
    suspend fun confirmExpiredSale(): CheckoutOutcome? = lock.withLock {
        val payments = pendingPayments ?: return@withLock null
        completeLocked(payments, allowExpired = true)
    }

    private suspend fun completeLocked(payments: List<PaymentInput>, allowExpired: Boolean): CheckoutOutcome {
        val cart = _cart.value
        if (cart.isEmpty) {
            _message.value = CashierMessage.EmptyCart
            return CheckoutOutcome.Failed
        }
        return try {
            val result = sales.completeSale(
                cart.toDraft(user.id, openShiftId()), payments, SaleOptions(allowNegativeStock, allowExpired),
            )
            pendingPayments = null
            startNewCart()
            CheckoutOutcome.Success(result)
        } catch (e: SaleException) {
            _message.value = when (e) {
                is SaleException.InsufficientPayment -> CashierMessage.PaymentShort(e.total, e.paid)
                is SaleException.NonCashOverpayment -> CashierMessage.CardOverpay
                is SaleException.EmptyCart -> CashierMessage.EmptyCart
                is SaleException.ExpiredProduct -> {
                    pendingPayments = payments
                    CashierMessage.ConfirmExpiredSale
                }
                is SaleException.InsufficientStock ->
                    CashierMessage.OutOfStock(db.products().getById(e.productId)?.name.orEmpty())
                is SaleException.InvalidQuantity, is SaleException.InvalidDiscount, is SaleException.InvalidPayment ->
                    CashierMessage.InvalidInput
                is SaleException.ProductUnavailable, is SaleException.AlreadyFinalized -> CashierMessage.SaleFailed
            }
            CheckoutOutcome.Failed
        }
    }

    // ---------- internals ----------
    private fun isAdmin() = user.role == UserRole.ADMIN

    private suspend fun openShiftId(): Long? = db.shifts().getOpenShift()?.id

    private suspend fun restore(draft: SaleDraft): Cart {
        val byId = draft.lines.map { it.productId }.distinct()
            .mapNotNull { db.products().getById(it) }.associateBy { it.id }
        return Cart.restore(draft, taxBasisPoints, byId)
    }

    private suspend fun startNewCart() {
        _cart.value = Cart(newUuid(), taxBasisPoints = taxBasisPoints)
        upsertCurrent()
    }

    private suspend fun upsertCurrent() {
        db.settings().upsert(AppSetting(CURRENT_CART_KEY, _cart.value.uuid))
    }

    /** Auto-save: a non-empty cart is stored as a HELD invoice; an emptied cart removes its draft. */
    private suspend fun persist() {
        val c = _cart.value
        upsertCurrent()
        if (c.isEmpty) {
            deleteDraftRow(c.uuid)
            return
        }
        try {
            sales.holdInvoice(c.toDraft(user.id, openShiftId()))
        } catch (e: SaleException) {
            // not saveable in this state; completeSale re-validates everything
        }
    }

    private suspend fun deleteDraftRow(uuid: String) {
        val inv = db.sales().getByUuid(uuid)
        if (inv != null && inv.status == InvoiceStatus.HELD) db.sales().deleteHeld(inv.id)
    }

    companion object {
        const val CURRENT_CART_KEY = "current_cart_uuid"
    }
}

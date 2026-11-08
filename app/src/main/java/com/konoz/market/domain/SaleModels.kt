package com.konoz.market.domain

import com.konoz.market.data.entity.PaymentMethod

/** One line of the cart. Price is the price actually charged (manager may have overridden it). */
data class CartLine(
    val productId: Long,
    val qtyMilli: Long,
    val unitPrice: Long,
    /** Discount on this line, in piastres (<= line gross). */
    val discount: Long = 0,
    val barcode: String? = null,
)

/** [uuid] is generated once when the cart is created and reused for retries: it is the idempotency key. */
data class SaleDraft(
    val uuid: String,
    val userId: Long,
    val shiftId: Long? = null,
    val lines: List<CartLine>,
    val invoiceDiscount: Long = 0,
    val taxBasisPoints: Int = 0,
    val notes: String? = null,
)

data class PaymentInput(val method: PaymentMethod, val amount: Long)

data class SaleOptions(
    /** Sell even when stock is short (stock goes negative). Mirrors the allow_negative_stock setting. */
    val allowNegativeStock: Boolean = true,
    /** Cashier confirmed selling expired stock after the warning. */
    val allowExpired: Boolean = false,
)

data class SaleResult(
    val invoiceId: Long,
    val number: Long,
    val total: Long,
    val paidTotal: Long,
    val changeDue: Long,
    val totalCost: Long,
    /** true when this uuid was already completed and nothing was posted again. */
    val duplicate: Boolean = false,
)

sealed class SaleException(message: String) : Exception(message) {
    class EmptyCart : SaleException("الفاتورة فارغة")
    class InvalidQuantity(val productId: Long) : SaleException("كمية غير صحيحة للمنتج $productId")
    class InvalidDiscount : SaleException("قيمة الخصم غير صحيحة")
    class InvalidPayment : SaleException("مبلغ الدفع غير صحيح")
    class ProductUnavailable(val productId: Long) : SaleException("المنتج $productId غير متاح للبيع")
    class InsufficientPayment(val total: Long, val paid: Long) : SaleException("المبلغ المدفوع أقل من إجمالي الفاتورة")
    class NonCashOverpayment : SaleException("الدفع بغير النقد لا يمكن أن يتجاوز إجمالي الفاتورة")
    class ExpiredProduct(val productId: Long) : SaleException("المنتج $productId منتهي الصلاحية")
    class InsufficientStock(val productId: Long, val missingMilli: Long) : SaleException("المخزون غير كافٍ للمنتج $productId")
    class AlreadyFinalized : SaleException("الفاتورة مكتملة أو ملغاة مسبقًا")
}

package com.konoz.market.domain

/**
 * One line of a purchase invoice (or of a return to the supplier when [PurchaseDraft.isReturn]).
 * unitCost is what the supplier charges (or credits) per base unit; discount is this line's discount in piastres.
 */
data class PurchaseLine(
    val productId: Long,
    val qtyMilli: Long,
    val unitCost: Long,
    val discount: Long = 0,
    val expiryDate: Long? = null,
    val batchNo: String? = null,
)

/** [uuid] is generated once per document and reused for retries: it is the idempotency key. */
data class PurchaseDraft(
    val uuid: String,
    val supplierId: Long,
    val userId: Long,
    val lines: List<PurchaseLine>,
    val invoiceDiscount: Long = 0,
    /** Paid to the supplier now (purchase) or refunded by the supplier now (return). */
    val paid: Long = 0,
    val notes: String? = null,
    val isReturn: Boolean = false,
)

data class PurchaseTotals(
    val subtotal: Long,
    val invoiceDiscount: Long,
    val total: Long,
    /** Per-line total after the line discount (before the invoice discount). */
    val lineNets: List<Long>,
)

data class PurchaseResult(
    val invoiceId: Long,
    val number: Long,
    val total: Long,
    val paid: Long,
    val remaining: Long,
    val isReturn: Boolean,
    /** true when this uuid was already approved and nothing was posted again. */
    val duplicate: Boolean = false,
)

sealed class PurchaseException(message: String) : Exception(message) {
    class EmptyLines : PurchaseException("الفاتورة لا تحتوي على منتجات")
    class InvalidQuantity(val productId: Long) : PurchaseException("كمية غير صحيحة للمنتج $productId")
    class InvalidAmount : PurchaseException("مبلغ أو خصم غير صحيح")
    class SupplierNotFound : PurchaseException("المورد غير موجود")
    class ProductUnavailable(val productId: Long) : PurchaseException("المنتج $productId غير موجود")
    class AlreadyFinalized : PurchaseException("الفاتورة معتمدة أو ملغاة مسبقًا")
    class InsufficientStockForReturn(val productId: Long, val missingMilli: Long) :
        PurchaseException("المخزون لا يكفي لإرجاع المنتج $productId")
    class InvoiceNotFound : PurchaseException("الفاتورة غير موجودة")
    class InvoiceNotApproved : PurchaseException("الفاتورة غير معتمدة")
}

package com.konoz.market.domain

import com.konoz.market.data.entity.PaymentMethod
import com.konoz.market.data.entity.ReturnCondition
import com.konoz.market.data.entity.SalesInvoice
import com.konoz.market.data.entity.SalesItem

data class ReturnLine(
    val salesItemId: Long,
    val qtyMilli: Long,
    val condition: ReturnCondition = ReturnCondition.RESELLABLE,
)

/** [uuid] is generated once per return screen and reused for retries. */
data class ReturnDraft(
    val uuid: String,
    val salesInvoiceId: Long,
    val userId: Long,
    val lines: List<ReturnLine>,
    val reason: String? = null,
    val refundMethod: PaymentMethod = PaymentMethod.CASH,
)

data class ReturnResult(val returnId: Long, val refundAmount: Long, val duplicate: Boolean = false)

data class ReturnableLine(val item: SalesItem, val returnedMilli: Long) {
    val remainingMilli: Long get() = item.qtyMilli - returnedMilli
}

data class ReturnableInvoice(val invoice: SalesInvoice, val lines: List<ReturnableLine>)

sealed class ReturnException(message: String) : Exception(message) {
    class EmptyReturn : ReturnException("لم يتم اختيار أي منتج للإرجاع")
    class InvoiceNotFound : ReturnException("الفاتورة غير موجودة")
    class InvoiceNotCompleted : ReturnException("الفاتورة غير مكتملة")
    class ItemNotInInvoice(val salesItemId: Long) : ReturnException("البند $salesItemId ليس من هذه الفاتورة")
    class InvalidQuantity(val salesItemId: Long) : ReturnException("كمية غير صحيحة للبند $salesItemId")
    class QuantityExceeds(val salesItemId: Long, val availableMilli: Long) :
        ReturnException("الكمية المرتجعة أكبر من المتاح للإرجاع للبند $salesItemId")
}

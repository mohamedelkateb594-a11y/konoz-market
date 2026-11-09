package com.konoz.market.domain

import com.konoz.market.data.entity.PurchaseInvoice
import com.konoz.market.data.entity.PurchaseStatus

object SupplierBalances {
    /** supplierId -> what we owe them (negative = they owe us). Only APPROVED invoices count. */
    fun of(invoices: List<PurchaseInvoice>): Map<Long, Long> =
        invoices.filter { it.status == PurchaseStatus.APPROVED }
            .groupBy { it.supplierId }
            .mapValues { (_, list) -> list.sumOf { if (it.isReturn) -it.remaining else it.remaining } }
}

package com.konoz.market.data.repo

import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.PurchaseInvoice
import com.konoz.market.data.entity.Supplier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Suppliers and the read-side of their invoices (categories live in ProductRepository). */
class CatalogRepository(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun observeSuppliers(): Flow<List<Supplier>> = db.suppliers().observeAll()

    fun observeAllInvoices(): Flow<List<PurchaseInvoice>> = db.purchases().observeAll()

    fun observeSupplierInvoices(supplierId: Long): Flow<List<PurchaseInvoice>> = db.purchases().observeForSupplier(supplierId)

    suspend fun getSupplier(id: Long): Supplier? = db.suppliers().getById(id)

    suspend fun addSupplier(name: String, phone: String?): Long = saveSupplier(0, name, phone, null, null)

    /** id = 0 creates, otherwise updates. Blank name is rejected. */
    suspend fun saveSupplier(id: Long, name: String, phone: String?, address: String?, notes: String?): Long {
        val n = name.trim()
        if (n.isEmpty()) throw ProductException.BlankName()
        fun clean(s: String?) = s?.trim()?.takeIf { it.isNotEmpty() }
        return if (id == 0L) {
            db.suppliers().insert(
                Supplier(name = n, phone = clean(phone), address = clean(address), notes = clean(notes), createdAt = clock())
            )
        } else {
            val existing = db.suppliers().getById(id) ?: throw ProductException.NotFound()
            db.suppliers().update(existing.copy(name = n, phone = clean(phone), address = clean(address), notes = clean(notes)))
            id
        }
    }

    /** Returns false (and deletes nothing) when the supplier has any invoice: purchase history must be kept. */
    suspend fun deleteSupplier(id: Long): Boolean {
        if (db.purchases().observeForSupplier(id).first().isNotEmpty()) return false
        db.suppliers().delete(id)
        return true
    }
}

package com.konoz.market.data.repo

import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.Supplier
import kotlinx.coroutines.flow.Flow

/** Small catalog helpers that do not belong to a single product: suppliers (categories live in ProductRepository). */
class CatalogRepository(
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun observeSuppliers(): Flow<List<Supplier>> = db.suppliers().observeAll()

    suspend fun addSupplier(name: String, phone: String?): Long {
        val n = name.trim()
        if (n.isEmpty()) throw ProductException.BlankName()
        return db.suppliers().insert(
            Supplier(name = n, phone = phone?.trim()?.takeIf { it.isNotEmpty() }, createdAt = clock())
        )
    }
}

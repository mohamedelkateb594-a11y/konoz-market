package com.konoz.market.data.repo

import com.konoz.market.core.Csv
import com.konoz.market.core.ExpiryDates
import com.konoz.market.core.Money
import com.konoz.market.core.Quantities
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.UnitType
import kotlinx.coroutines.flow.first

data class ImportError(val line: Int, val message: String)

data class ImportResult(val created: Int, val updated: Int, val errors: List<ImportError>)

/**
 * Product import/export as CSV (opens in Excel; native .xlsx is not supported).
 *
 * Columns: name, internal_code, category, unit, cost_price, sale_price, low_stock, pieces_per_carton,
 *          barcodes ("|" separated, "code*units" for carton barcodes), quantity, expiry (yyyy-MM-dd), active (1/0)
 *
 * Import rules:
 *  - A row updates an existing product when its internal_code (or, failing that, its first barcode) matches;
 *    otherwise it creates a new one. `quantity` is the OPENING stock of NEW products only: stock of existing
 *    products never changes through import (it changes through movements).
 *  - Every row is saved in its own transaction; a bad row is reported with its line number and skipped.
 */
class ProductCsvService(
    private val db: KonozDatabase,
    private val repo: ProductRepository,
) {
    suspend fun exportCsv(): String {
        val products = db.products().observeAll().first()
        val categories = db.categories().observeAll().first().associateBy { it.id }
        val rows = ArrayList<List<String>>()
        rows += HEADERS
        for (p in products) {
            val barcodes = db.barcodes().getForProduct(p.id)
                .joinToString("|") { if (it.unitsPerScan == 1) it.code else "${it.code}*${it.unitsPerScan}" }
            rows += listOf(
                p.name,
                p.internalCode.orEmpty(),
                p.categoryId?.let { categories[it]?.name }.orEmpty(),
                p.unit.name,
                Money.fromMinor(p.costPrice).toPlainString(),
                Money.fromMinor(p.salePrice).toPlainString(),
                Quantities.format(p.lowStockThresholdMilli),
                p.piecesPerCarton?.toString().orEmpty(),
                barcodes,
                Quantities.format(p.currentQtyMilli),
                p.expiryDate?.let { ExpiryDates.format(it) }.orEmpty(),
                if (p.isActive) "1" else "0",
            )
        }
        return Csv.write(rows)
    }

    suspend fun importCsv(text: String, userId: Long?): ImportResult {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) return ImportResult(0, 0, listOf(ImportError(1, "empty file")))
        val index = rows[0].withIndex().associate { it.value.trim().lowercase() to it.index }
        if ("name" !in index) return ImportResult(0, 0, listOf(ImportError(1, "missing column: name")))

        var created = 0
        var updated = 0
        val errors = ArrayList<ImportError>()
        for ((i, row) in rows.withIndex()) {
            if (i == 0 || row.all { it.isBlank() }) continue
            val line = i + 1
            fun col(name: String): String = index[name]?.let { row.getOrNull(it) }?.trim().orEmpty()
            try {
                val unitText = col("unit").ifEmpty { "PIECE" }.uppercase()
                val unit = UnitType.values().firstOrNull { it.name == unitText } ?: throw IllegalArgumentException("unit: $unitText")
                val cost = Money.parseToMinor(col("cost_price").ifEmpty { "0" }) ?: throw IllegalArgumentException("cost_price")
                val price = Money.parseToMinor(col("sale_price").ifEmpty { "0" }) ?: throw IllegalArgumentException("sale_price")
                val low = Quantities.parseMilli(col("low_stock").ifEmpty { "0" }) ?: throw IllegalArgumentException("low_stock")
                val quantity = Quantities.parseMilli(col("quantity").ifEmpty { "0" }) ?: throw IllegalArgumentException("quantity")
                val perCarton = col("pieces_per_carton").takeIf { it.isNotEmpty() }?.let {
                    Money.normalizeDigits(it).toIntOrNull() ?: throw IllegalArgumentException("pieces_per_carton")
                }
                val expiry = col("expiry").takeIf { it.isNotEmpty() }?.let {
                    ExpiryDates.parse(it) ?: throw IllegalArgumentException("expiry: $it")
                }
                val active = when (col("active").lowercase().ifEmpty { "1" }) {
                    "1", "true", "yes", "نعم" -> true
                    "0", "false", "no", "لا" -> false
                    else -> throw IllegalArgumentException("active")
                }
                val barcodes = col("barcodes").split('|').map { it.trim() }.filter { it.isNotEmpty() }.map { token ->
                    val parts = token.split('*')
                    val units = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: throw IllegalArgumentException("barcode units: $token") else 1
                    BarcodeInput(parts[0].trim(), units)
                }
                val categoryName = col("category")
                val categoryId = if (categoryName.isNotEmpty()) repo.addCategory(categoryName) else null
                val code = col("internal_code").takeIf { it.isNotEmpty() }

                val existing = (code?.let { db.products().findByInternalCode(it) })
                    ?: barcodes.firstOrNull()?.let { b -> db.barcodes().findByCode(b.code)?.let { db.products().getById(it.productId) } }

                if (existing != null) {
                    repo.save(
                        ProductInput(
                            id = existing.id, name = col("name"), internalCode = code ?: existing.internalCode,
                            categoryId = categoryId, supplierId = existing.supplierId, unit = unit,
                            costPrice = cost, salePrice = price, lowStockThresholdMilli = low, piecesPerCarton = perCarton,
                            expiryDate = expiry, imagePath = existing.imagePath, isFavorite = existing.isFavorite, isActive = active,
                            barcodes = barcodes.ifEmpty { db.barcodes().getForProduct(existing.id).map { BarcodeInput(it.code, it.unitsPerScan) } },
                        ),
                        userId,
                    )
                    updated++
                } else {
                    repo.save(
                        ProductInput(
                            name = col("name"), internalCode = code, categoryId = categoryId, unit = unit,
                            costPrice = cost, salePrice = price, lowStockThresholdMilli = low, piecesPerCarton = perCarton,
                            expiryDate = expiry, isActive = active, barcodes = barcodes, openingQtyMilli = quantity,
                        ),
                        userId,
                    )
                    created++
                }
            } catch (e: ProductException) {
                errors += ImportError(line, e.message.orEmpty())
            } catch (e: IllegalArgumentException) {
                errors += ImportError(line, e.message.orEmpty())
            }
        }
        return ImportResult(created, updated, errors)
    }

    companion object {
        val HEADERS = listOf(
            "name", "internal_code", "category", "unit", "cost_price", "sale_price", "low_stock",
            "pieces_per_carton", "barcodes", "quantity", "expiry", "active",
        )
    }
}

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
 * Columns (the header row may be written in Arabic or English, see [HEADER_ALIASES]):
 *   name, internal_code, category, unit, cost_price, sale_price, low_stock, pieces_per_carton,
 *   barcodes ("|" separated, "code*units" for carton barcodes), quantity, expiry (yyyy-MM-dd), active (1/0)
 * The unit may be an English name (PIECE, CARTON, KG, GRAM, LITER, PACK) or an Arabic one (see [UNIT_ALIASES]).
 * The export writes an Arabic header row and Arabic unit names; importing that file back works unchanged.
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
        rows += EXPORT_HEADERS
        for (p in products) {
            val barcodes = db.barcodes().getForProduct(p.id)
                .joinToString("|") { if (it.unitsPerScan == 1) it.code else "${it.code}*${it.unitsPerScan}" }
            rows += listOf(
                p.name,
                p.internalCode.orEmpty(),
                p.categoryId?.let { categories[it]?.name }.orEmpty(),
                unitArabic(p.unit),
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
        // Header name (Arabic or English) -> canonical English key -> column position. The first column of a key wins.
        val index = HashMap<String, Int>()
        for ((position, header) in rows[0].withIndex()) {
            val normalized = normalize(header)
            index.putIfAbsent(HEADER_ALIASES[normalized] ?: normalized, position)
        }
        if ("name" !in index) return ImportResult(0, 0, listOf(ImportError(1, "missing column: name (الاسم)")))

        var created = 0
        var updated = 0
        val errors = ArrayList<ImportError>()
        for ((i, row) in rows.withIndex()) {
            if (i == 0 || row.all { it.isBlank() }) continue
            val line = i + 1
            fun col(name: String): String = index[name]?.let { row.getOrNull(it) }?.trim().orEmpty()
            try {
                val unitText = normalize(col("unit").ifEmpty { "PIECE" })
                val unit = UNIT_ALIASES[unitText]
                    ?: UnitType.values().firstOrNull { it.name.lowercase() == unitText }
                    ?: throw IllegalArgumentException("unit: ${col("unit")}")
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
                val active = when (normalize(col("active")).ifEmpty { "1" }) {
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
        /** The canonical (English) keys, in file order. */
        val HEADERS = listOf(
            "name", "internal_code", "category", "unit", "cost_price", "sale_price", "low_stock",
            "pieces_per_carton", "barcodes", "quantity", "expiry", "active",
        )

        /** The header row the export writes (same order as [HEADERS]). */
        val EXPORT_HEADERS = listOf(
            "الاسم", "الكود الداخلي", "التصنيف", "الوحدة", "سعر الشراء", "سعر البيع", "حد التنبيه",
            "قطع الكرتونة", "الباركود", "الكمية", "الصلاحية", "مفعل",
        )

        /** Accepted Arabic header names (already normalized) -> canonical key. English keys need no alias. */
        private val HEADER_ALIASES: Map<String, String> = buildMap {
            fun alias(key: String, vararg names: String) = names.forEach { put(normalize(it), key) }
            alias("name", "الاسم", "اسم المنتج", "اسم الصنف", "المنتج", "الصنف")
            alias("internal_code", "الكود الداخلي", "الكود", "كود", "كود المنتج")
            alias("category", "التصنيف", "القسم", "الفئة")
            alias("unit", "الوحدة", "وحدة القياس")
            alias("cost_price", "سعر الشراء", "سعر التكلفة", "التكلفة", "الشراء")
            alias("sale_price", "سعر البيع", "السعر", "البيع")
            alias("low_stock", "حد التنبيه", "حد الطلب", "تنبيه المخزون", "الحد الأدنى", "تنبيه انخفاض المخزون")
            alias("pieces_per_carton", "قطع الكرتونة", "عدد القطع في الكرتونة", "القطع في الكرتونة", "قطع الكرتون")
            alias("barcodes", "الباركود", "الباركودات", "باركود")
            alias("quantity", "الكمية", "الكمية الافتتاحية", "المخزون", "الرصيد")
            alias("expiry", "الصلاحية", "تاريخ الصلاحية", "تاريخ الانتهاء")
            alias("active", "مفعل", "نشط", "فعال", "متاح للبيع")
        }

        /** Accepted unit names (normalized): the Arabic ones; English ones are matched against [UnitType] names. */
        private val UNIT_ALIASES: Map<String, UnitType> = buildMap {
            fun alias(unit: UnitType, vararg names: String) = names.forEach { put(normalize(it), unit) }
            alias(UnitType.PIECE, "قطعة", "قطعه", "حبة", "حبه")
            alias(UnitType.CARTON, "كرتونة", "كرتونه", "كرتون", "كارتونة")
            alias(UnitType.KG, "كيلو", "كجم", "كيلوجرام", "كيلو جرام")
            alias(UnitType.GRAM, "جرام", "جم", "غرام")
            alias(UnitType.LITER, "لتر")
            alias(UnitType.PACK, "عبوة", "عبوه", "علبة", "علبه", "باكيت")
        }

        private fun unitArabic(unit: UnitType): String = when (unit) {
            UnitType.PIECE -> "قطعة"
            UnitType.CARTON -> "كرتونة"
            UnitType.KG -> "كيلو"
            UnitType.GRAM -> "جرام"
            UnitType.LITER -> "لتر"
            UnitType.PACK -> "عبوة"
        }

        /** Trims, lower-cases, removes Arabic diacritics and tatweel, collapses spaces and treats "_" as a space for matching. */
        private fun normalize(text: String): String {
            val sb = StringBuilder()
            for (c in text.trim().lowercase()) {
                if (c in '\u064B'..'\u0652' || c == '\u0640') continue
                sb.append(if (c == '_') ' ' else c)
            }
            return sb.toString().replace(Regex("\\s+"), " ").trim()
        }
    }
}

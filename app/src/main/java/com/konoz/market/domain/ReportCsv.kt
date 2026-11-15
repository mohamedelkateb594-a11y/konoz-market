package com.konoz.market.domain

import com.konoz.market.core.Money
import java.math.BigDecimal

/**
 * Builds the CSV text of a period report from data [ReportService] already computed (no database access, no rounding of its own).
 *
 *  - Money is written as plain decimals ("12.50"), quantities with up to 3 decimals, so spreadsheets can sum them.
 *  - Fields are quoted when they hold a comma, quote or line break; text starting with = + - @ gets a leading apostrophe so
 *    a product name can never run as a spreadsheet formula.
 *  - [includeProfit] = false leaves out costs and profits (for users who may not see them).
 *  - Write the result as UTF-8 and start the file with [BOM] so Excel shows Arabic text correctly.
 */
object ReportCsv {
    const val BOM = "﻿"
    private const val NO_CATEGORY = "بدون تصنيف"

    fun build(
        summary: PeriodSummary,
        products: List<ProductPerformance>,
        categories: List<CategorySales>,
        expenses: List<ExpenseByCategory>,
        includeProfit: Boolean,
        formatDate: (Long) -> String,
    ): String {
        val out = StringBuilder()
        fun row(vararg cells: String) {
            out.append(cells.joinToString(",") { field(it) }).append("\r\n")
        }

        row("التقرير", "من", "إلى")
        row("ملخص الفترة", formatDate(summary.range.from), formatDate(summary.range.to))
        out.append("\r\n")

        row("البند", "القيمة")
        row("إجمالي المبيعات (شامل الضريبة)", money(summary.grossSales))
        row("الخصومات", money(summary.discounts))
        row("الضريبة المحصّلة", money(summary.taxCollected))
        row("عدد الفواتير", summary.invoiceCount.toString())
        row("متوسط الفاتورة", money(summary.averageInvoice))
        row("المرتجعات", money(summary.returnsTotal))
        row("صافي المبيعات (بدون ضريبة)", money(summary.netSales))
        row("المصروفات", money(summary.expenses))
        if (includeProfit) {
            row("تكلفة البضاعة المباعة", money(summary.cogs))
            row("مجمل الربح", money(summary.grossProfit))
            row("خسائر المخزون", money(summary.inventoryLosses))
            row("صافي الربح التقديري", money(summary.estimatedNetProfit))
        }
        out.append("\r\n")

        row("المنتجات")
        if (includeProfit) row("المنتج", "التصنيف", "الكمية", "الإيراد", "التكلفة", "الربح")
        else row("المنتج", "التصنيف", "الكمية", "الإيراد")
        for (p in products.sortedWith(compareByDescending<ProductPerformance> { it.revenue }.thenBy { it.name })) {
            val base = arrayOf(p.name, p.categoryName ?: NO_CATEGORY, qty(p.qtyMilli), money(p.revenue))
            if (includeProfit) row(*base, money(p.cost), money(p.profit)) else row(*base)
        }
        out.append("\r\n")

        row("التصنيفات")
        if (includeProfit) row("التصنيف", "الإيراد", "التكلفة", "الربح")
        else row("التصنيف", "الإيراد")
        for (c in categories) {
            val name = c.categoryName ?: NO_CATEGORY
            if (includeProfit) row(name, money(c.revenue), money(c.cost), money(c.profit)) else row(name, money(c.revenue))
        }
        out.append("\r\n")

        row("المصروفات حسب النوع")
        row("النوع", "الإجمالي")
        for (e in expenses) row(e.category.name, money(e.total))
        return out.toString()
    }

    private fun money(minor: Long): String = Money.fromMinor(minor).toPlainString()

    private fun qty(milli: Long): String = BigDecimal(milli).movePointLeft(3).stripTrailingZeros().toPlainString()

    /** RFC 4180 quoting plus a guard against spreadsheet formulas. Numbers (incl. negatives) are written by [money]/[qty] and are not text, so only real text is guarded. */
    private fun field(raw: String): String {
        val isNumber = raw.matches(NUMBER)
        val safe = if (!isNumber && raw.isNotEmpty() && raw[0] in FORMULA_STARTS) "'$raw" else raw
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private val NUMBER = Regex("^-?[0-9]+(\\.[0-9]+)?$")
    private val FORMULA_STARTS = charArrayOf('=', '+', '-', '@')
}

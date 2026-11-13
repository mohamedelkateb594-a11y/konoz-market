package com.konoz.market.domain

import com.konoz.market.data.entity.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ReportCsvTest {

    private val range = Periods.rangeOf(PeriodKind.DAY, 1_700_000_000_000L, TimeZone.getTimeZone("UTC"))

    private val summary = PeriodSummary(
        range = range, grossSales = 115_000, discounts = 5_000, taxCollected = 15_000, invoiceCount = 4,
        averageInvoice = 28_750, returnsTotal = 11_500, returnsTax = 1_500, netSales = 90_000, cogs = 60_000,
        grossProfit = 30_000, expenses = 10_000, inventoryLosses = 2_000, estimatedNetProfit = 18_000,
    )

    private fun product(name: String, qtyMilli: Long, revenue: Long, cost: Long, category: String? = "ألبان") =
        ProductPerformance(1, name, category, qtyMilli, revenue, cost, revenue - cost)

    private fun csv(
        products: List<ProductPerformance> = emptyList(),
        includeProfit: Boolean = true,
    ) = ReportCsv.build(
        summary, products,
        categories = listOf(CategorySales("ألبان", 90_000, 60_000, 30_000), CategorySales(null, 500, 300, 200)),
        expenses = listOf(ExpenseByCategory(ExpenseCategory.valueOf(ExpenseCategory.values().first().name), 10_000)),
        includeProfit = includeProfit,
        formatDate = { "D$it" },
    )

    private fun lines(text: String) = text.split("\r\n")

    @Test fun money_is_plain_decimals_and_the_summary_is_complete() {
        val text = csv()

        assertTrue(text.contains("إجمالي المبيعات (شامل الضريبة),1150.00"))
        assertTrue(text.contains("صافي المبيعات (بدون ضريبة),900.00"))
        assertTrue(text.contains("صافي الربح التقديري,180.00"))
        assertTrue(text.contains("عدد الفواتير,4"))
    }

    @Test fun profit_figures_are_left_out_when_not_allowed() {
        val text = csv(listOf(product("لبن", 2_000, 3000, 2000)), includeProfit = false)

        assertFalse(text.contains("مجمل الربح"))
        assertFalse(text.contains("صافي الربح"))
        assertFalse(text.contains("تكلفة"))
        assertFalse(text.contains("الربح"))
        assertTrue(text.contains("لبن,ألبان,2,30.00")) // name, category, qty, revenue only
    }

    @Test fun products_are_sorted_by_revenue_and_quantities_keep_up_to_three_decimals() {
        val text = csv(
            listOf(
                product("A", 1_500, 1000, 400),
                product("B", 2_000, 5000, 1000),
            )
        )
        val l = lines(text)
        val b = l.indexOfFirst { it.startsWith("B,") }
        val a = l.indexOfFirst { it.startsWith("A,") }
        assertTrue(b in 0 until a) // higher revenue first
        assertEquals("A,ألبان,1.5,10.00,4.00,6.00", l[a])
        assertEquals("B,ألبان,2,50.00,10.00,40.00", l[b])
    }

    @Test fun a_product_without_a_category_gets_a_label() {
        val text = csv(listOf(product("X", 1000, 100, 50, category = null)))
        assertTrue(text.contains("X,بدون تصنيف,1,1.00,0.50,0.50"))
    }

    @Test fun commas_quotes_and_line_breaks_are_quoted() {
        val text = csv(listOf(product("زيت, \"ممتاز\"\nلتر", 1000, 100, 50)))
        assertTrue(text.contains("\"زيت, \"\"ممتاز\"\"\nلتر\","))
    }

    @Test fun text_that_looks_like_a_formula_is_neutralised_but_negative_numbers_are_not() {
        val text = csv(listOf(product("=HYPERLINK(\"x\")", 1000, -500, 0), product("+cmd", 1000, 100, 50)))
        assertTrue(text.contains("'=HYPERLINK"))
        assertTrue(text.contains("'+cmd"))
        assertTrue(text.contains(",-5.00,")) // a real negative revenue stays a number
    }

    @Test fun every_row_ends_with_crlf_and_the_bom_is_a_single_char() {
        val text = csv()
        assertTrue(text.endsWith("\r\n"))
        assertEquals(1, ReportCsv.BOM.length)
        assertFalse(text.contains("\n") && text.replace("\r\n", "").contains("\n")) // no bare line feeds in this data
    }
}

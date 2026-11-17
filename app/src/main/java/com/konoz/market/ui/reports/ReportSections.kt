package com.konoz.market.ui.reports

/**
 * The sections of the Reports screen, in the order the screen draws them after the period chips.
 * A dashboard card opens the Reports screen scrolled to one of them (route `reports/<id>` in the More tab).
 * If you add, remove or reorder a section in ReportsScreen, change [order] to match.
 */
object ReportSections {
    const val SUMMARY = "summary"
    const val TOP_QTY = "topqty"
    const val TOP_PROFIT = "topprofit"
    const val LEAST_SOLD = "leastsold"
    const val NON_MOVING = "nonmoving"
    const val CATEGORIES = "categories"
    const val EXPENSES = "expenses"
    const val PEAK_HOURS = "peakhours"
    const val WEEKDAYS = "weekdays"

    val order: List<String> = listOf(SUMMARY, TOP_QTY, TOP_PROFIT, LEAST_SOLD, NON_MOVING, CATEGORIES, EXPENSES, PEAK_HOURS, WEEKDAYS)
}

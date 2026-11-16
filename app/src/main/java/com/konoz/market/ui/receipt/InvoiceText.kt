package com.konoz.market.ui.receipt

import android.content.Context
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.core.Quantities
import com.konoz.market.domain.InvoiceData
import com.konoz.market.ui.shop.ShopProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The invoice as plain text for a WhatsApp message: shop name, number, items, totals. */
object InvoiceText {
    fun build(context: Context, d: InvoiceData, money: (Long) -> String = { Money.format(it) }): String = buildString {
        appendLine(ShopProfile.displayName(context))
        appendLine(context.getString(R.string.inv_number_fmt, d.number))
        appendLine(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(d.dateMillis)))
        appendLine("--------------------")
        for (l in d.lines) {
            appendLine(l.name)
            appendLine("   " + context.getString(R.string.inv_qty_price_fmt, Quantities.format(l.qtyMilli), money(l.unitPrice)) + " = " + money(l.total))
        }
        appendLine("--------------------")
        if (d.invoiceDiscount > 0L) appendLine(context.getString(R.string.inv_invoice_discount) + ": -" + money(d.invoiceDiscount))
        if (d.taxAmount > 0L) appendLine(context.getString(R.string.inv_tax) + ": " + money(d.taxAmount))
        appendLine(context.getString(R.string.inv_total) + ": " + money(d.total))
        append(context.getString(R.string.inv_thanks))
    }
}

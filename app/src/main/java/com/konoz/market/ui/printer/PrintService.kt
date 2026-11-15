package com.konoz.market.ui.printer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.konoz.market.R
import com.konoz.market.data.KonozDatabase
import com.konoz.market.domain.EscPos
import com.konoz.market.domain.InvoiceLoader
import com.konoz.market.ui.receipt.InvoiceRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

enum class PaperWidth(val dots: Int, val mm: Int) { MM58(EscPos.DOTS_58, 58), MM80(EscPos.DOTS_80, 80) }

/** The printer the user chose, remembered on this phone (not in the database, so a restored backup keeps it). */
class PrinterSettings(private val context: Context) {
    private val prefs get() = context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)

    var address: String?
        get() = prefs.getString("address", null)
        set(v) = prefs.edit().putString("address", v).apply()

    var name: String?
        get() = prefs.getString("name", null)
        set(v) = prefs.edit().putString("name", v).apply()

    var width: PaperWidth
        get() = PaperWidth.values().firstOrNull { it.name == prefs.getString("width", null) } ?: PaperWidth.MM80
        set(v) = prefs.edit().putString("width", v.name).apply()
}

class PrintException(val reason: Reason, cause: Throwable? = null) : Exception(reason.name, cause) {
    enum class Reason { NO_PRINTER, NO_PERMISSION, BLUETOOTH_OFF, INVOICE_MISSING, FAILED }
}

/**
 * Prints invoices and a test page on the chosen Bluetooth printer. Other screens (the invoice send dialog)
 * call [printInvoice] with the invoice id.
 */
class PrintService(
    private val context: Context,
    private val db: KonozDatabase,
    val settings: PrinterSettings = PrinterSettings(context),
    val printer: ThermalPrinter = ThermalPrinter(context),
) {
    suspend fun printInvoice(invoiceId: Long) = withContext(Dispatchers.IO) {
        val data = InvoiceLoader(db).load(invoiceId) ?: throw PrintException(PrintException.Reason.INVOICE_MISSING)
        val page = InvoiceRenderer(context).prepare(data)
        sendPage(page.width, page.height) { canvas -> page.draw(canvas) }
    }

    suspend fun printTestPage() = withContext(Dispatchers.IO) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f; textAlign = Paint.Align.CENTER }
        val title = context.getString(R.string.printer_test_title)
        val line = context.getString(R.string.printer_test_line, settings.width.mm)
        sendPage(226, 90) { canvas ->
            canvas.drawText(title, 113f, 30f, paint)
            canvas.drawText(line, 113f, 56f, paint)
            canvas.drawRect(4f, 4f, 222f, 86f, Paint().apply { style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = 2f })
        }
    }

    /** Draws a page of [pageWidth] x [pageHeight] units scaled to the paper width and sends it. */
    private fun sendPage(pageWidth: Int, pageHeight: Int, draw: (Canvas) -> Unit) {
        val address = settings.address ?: throw PrintException(PrintException.Reason.NO_PRINTER)
        if (!printer.hasPermission()) throw PrintException(PrintException.Reason.NO_PERMISSION)
        if (!printer.isBluetoothOn()) throw PrintException(PrintException.Reason.BLUETOOTH_OFF)
        val dots = settings.width.dots
        val scale = dots.toFloat() / pageWidth
        val height = (pageHeight * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(dots, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.scale(scale, scale)
            draw(canvas)
            val pixels = IntArray(dots * height)
            bitmap.getPixels(pixels, 0, dots, 0, 0, dots, height)
            try {
                printer.send(address, EscPos.job(pixels, dots, height))
            } catch (e: IOException) {
                throw PrintException(PrintException.Reason.FAILED, e)
            }
        } finally {
            bitmap.recycle()
        }
    }
}

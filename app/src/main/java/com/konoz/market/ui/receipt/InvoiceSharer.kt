package com.konoz.market.ui.receipt

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.domain.InvoiceData
import java.io.File

enum class InvoiceFormat(val extension: String, val mime: String) {
    PDF("pdf", "application/pdf"),
    IMAGE("png", "image/png"),
}

enum class ShareResult {
    /** Opened in WhatsApp, addressed to the customer's number. */
    WHATSAPP,
    /** Opened the system share sheet. */
    CHOOSER,
    /** A WhatsApp number was given but WhatsApp is not installed; the share sheet was opened instead. */
    CHOOSER_NO_WHATSAPP,
}

/**
 * Writes an invoice to a temporary file and hands it to WhatsApp (to a given number) or to any app through the share sheet.
 * Files live in the cache folder and are replaced on every share; they are served through the app's FileProvider.
 */
class InvoiceSharer(private val context: Context, private val renderer: InvoiceRenderer = InvoiceRenderer(context)) {

    fun createFile(data: InvoiceData, format: InvoiceFormat): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "invoice-${data.number}.${format.extension}")
        val page = renderer.prepare(data)
        file.outputStream().use { out ->
            when (format) {
                InvoiceFormat.PDF -> renderer.writePdf(page, out)
                InvoiceFormat.IMAGE -> renderer.writePng(page, out)
            }
        }
        return file
    }

    /** Short text sent with the file. */
    fun caption(data: InvoiceData): String =
        context.getString(R.string.inv_caption_fmt, context.getString(R.string.inv_shop_name), data.number, Money.format(data.total))

    /**
     * [whatsappNumber] = digits with country code (see PhoneNumbers.toWhatsApp). With it, WhatsApp (or WhatsApp Business)
     * opens on that customer's chat with the file attached; without it, or when WhatsApp is missing, the share sheet opens.
     */
    fun share(file: File, format: InvoiceFormat, caption: String, whatsappNumber: String?): ShareResult {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        fun send() = Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, caption)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (whatsappNumber != null) {
            for (pkg in WHATSAPP_PACKAGES) {
                val intent = send().apply {
                    setPackage(pkg)
                    putExtra("jid", "$whatsappNumber@s.whatsapp.net") // opens the chat of that number
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(intent)
                    return ShareResult.WHATSAPP
                }
            }
        }
        val chooser = Intent.createChooser(send(), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        return if (whatsappNumber != null) ShareResult.CHOOSER_NO_WHATSAPP else ShareResult.CHOOSER
    }

    companion object {
        private const val DIR = "invoices"
        private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
    }
}

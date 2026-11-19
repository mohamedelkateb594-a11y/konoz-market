package com.konoz.market.ui.receipt

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.domain.InvoiceData
import com.konoz.market.ui.shop.ShopProfile
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

    /**
     * Short text sent with the file (starts with the shop name the owner chose in Shop settings).
     * When the invoice has a customer name, the text greets the customer by name first.
     */
    fun caption(data: InvoiceData): String {
        val base = context.getString(R.string.inv_caption_fmt, ShopProfile.displayName(context), data.number, Money.format(data.total))
        val name = data.customerName ?: return base
        return context.getString(R.string.inv_caption_customer_fmt, name, base)
    }

    /**
     * [whatsappNumber] = digits with country code (see PhoneNumbers.toWhatsApp). With it, WhatsApp (or WhatsApp Business)
     * opens on that customer's chat with the file attached; without it, or when WhatsApp is missing, the share sheet opens.
     *
     * Android has no documented way to attach a file to a given WhatsApp number. The "jid" extra is an undocumented key that
     * only some WhatsApp screens honor, so two targets are tried in order: the contact-picker screen (the one known to read
     * "jid"), then WhatsApp's generic share screen. Which one works depends on the installed WhatsApp version.
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
            val jid = "$whatsappNumber@s.whatsapp.net"
            for (pkg in WHATSAPP_PACKAGES) {
                val viaPicker = send().apply {
                    component = ComponentName(pkg, "com.whatsapp.ContactPicker")
                    putExtra("jid", jid)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (tryStart(viaPicker, pkg, uri)) return ShareResult.WHATSAPP
                val viaPackage = send().apply {
                    setPackage(pkg)
                    putExtra("jid", jid)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (tryStart(viaPackage, pkg, uri)) return ShareResult.WHATSAPP
            }
        }
        val chooser = Intent.createChooser(send(), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        return if (whatsappNumber != null) ShareResult.CHOOSER_NO_WHATSAPP else ShareResult.CHOOSER
    }

    /**
     * Opens WhatsApp straight on the chat of [number] (digits with country code) with [text] ready to send.
     * Nothing is launched and CHOOSER_NO_WHATSAPP is returned when WhatsApp is not installed.
     */
    fun openChat(number: String, text: String): ShareResult {
        val uri = Uri.parse("https://wa.me/$number?text=" + Uri.encode(text))
        for (pkg in WHATSAPP_PACKAGES) {
            val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return ShareResult.WHATSAPP
            }
        }
        return ShareResult.CHOOSER_NO_WHATSAPP
    }

    private fun tryStart(intent: Intent, pkg: String, uri: Uri): Boolean {
        if (intent.resolveActivity(context.packageManager) == null) return false
        return try {
            context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false // not exported on this WhatsApp version: the next target is tried
        }
    }

    companion object {
        private const val DIR = "invoices"
        private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
    }
}

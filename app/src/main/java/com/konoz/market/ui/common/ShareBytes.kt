package com.konoz.market.ui.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Like [shareTextFile] for binary content (PDF, Excel): writes [bytes] under cache/invoices/reports and opens the share sheet.
 * Returns false if the file could not be written or no app could be started. [fileName] must be a plain file name.
 */
suspend fun shareBytesFile(context: Context, fileName: String, bytes: ByteArray, mimeType: String, chooserTitle: String): Boolean {
    if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return false
    return try {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "invoices/reports").apply { mkdirs() }
            File(dir, fileName).also { it.writeBytes(bytes) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}

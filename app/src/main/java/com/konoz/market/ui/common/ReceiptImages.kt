package com.konoz.market.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Expense receipt photos are copied into the app's private storage (downscaled JPEG).
 * NOTE: like product pictures these live outside the database; the backup feature must include the receipts folder.
 */
object ReceiptImages {
    private const val MAX_SIDE = 1280

    /** Blocking: call from a background dispatcher. Returns the saved file path, or null on failure. */
    fun save(context: Context, uri: Uri): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = ProductImages.sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE) }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (bitmap == null) {
            null
        } else {
            val dir = File(context.filesDir, "receipts").apply { mkdirs() }
            val file = File(dir, UUID.randomUUID().toString() + ".jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            file.absolutePath
        }
    } catch (e: Exception) {
        null
    }
}

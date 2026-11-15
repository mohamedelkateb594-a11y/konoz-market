package com.konoz.market.domain

import org.json.JSONObject

/** Why a backup file could not be created, read or restored. The UI maps each to a message. */
enum class BackupFailure { NOT_A_BACKUP, CORRUPT, NEWER_VERSION, IO }

class BackupException(val failure: BackupFailure, message: String, cause: Throwable? = null) : Exception(message, cause)

/** First entry of every backup file: what it holds and how to check it. */
data class BackupManifest(
    val formatVersion: Int,
    val dbVersion: Int,
    val appVersion: String,
    val createdAt: Long,
    val dbSha256: String,
    val imageCount: Int,
    val receiptCount: Int,
    val salesInvoiceCount: Int,
) {
    fun toJson(): String = JSONObject()
        .put("app", APP_ID)
        .put("formatVersion", formatVersion)
        .put("dbVersion", dbVersion)
        .put("appVersion", appVersion)
        .put("createdAt", createdAt)
        .put("dbSha256", dbSha256)
        .put("imageCount", imageCount)
        .put("receiptCount", receiptCount)
        .put("salesInvoiceCount", salesInvoiceCount)
        .toString()

    companion object {
        const val APP_ID = "com.konoz.market"
        const val FORMAT_VERSION = 1

        /** Null when the text is not a Konoz manifest. */
        fun fromJson(text: String): BackupManifest? = try {
            val o = JSONObject(text)
            if (o.getString("app") != APP_ID) null else BackupManifest(
                formatVersion = o.getInt("formatVersion"),
                dbVersion = o.getInt("dbVersion"),
                appVersion = o.optString("appVersion"),
                createdAt = o.getLong("createdAt"),
                dbSha256 = o.getString("dbSha256"),
                imageCount = o.optInt("imageCount"),
                receiptCount = o.optInt("receiptCount"),
                salesInvoiceCount = o.optInt("salesInvoiceCount"),
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** A backup kept inside the app (automatic, manual or the safety copy taken before a restore). */
data class LocalBackup(val name: String, val sizeBytes: Long, val modifiedAt: Long, val automatic: Boolean)

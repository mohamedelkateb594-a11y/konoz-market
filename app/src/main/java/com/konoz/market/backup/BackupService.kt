package com.konoz.market.backup

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.AppSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

sealed class BackupException(message: String) : Exception(message) {
    /** Not a zip file at all. */
    class NotZip : BackupException("الملف ليس نسخة احتياطية صالحة")
    /** A zip, but not made by this app (or by an unknown backup format). */
    class NotKonozBackup : BackupException("هذا الملف ليس نسخة احتياطية من كنوز ماركت")
    class IncompatibleVersion(val backupVersion: Int, val appVersion: Int) : BackupException("نسخة قاعدة البيانات غير متوافقة مع هذا الإصدار")
    /** Damaged, tampered with or incomplete. */
    class Corrupt : BackupException("النسخة الاحتياطية تالفة أو غير كاملة")
    /** Restoring it would leave nobody able to sign in as admin. */
    class NoAdmin : BackupException("النسخة لا تحتوي على مدير نشط")
}

/** What a backup file contains, read from its manifest after the file was verified. */
data class BackupInfo(
    val createdAt: Long,
    val appVersion: String,
    val schemaVersion: Int,
    val invoices: Int,
    val products: Int,
    val users: Int,
    val receiptFiles: Int,
    val imageFiles: Int,
)

data class RestoreResult(val info: BackupInfo, val autoBackupEnabled: Boolean)

object BackupSettings {
    const val AUTO_ENABLED = "auto_backup_enabled"
    const val LAST_AT = "last_backup_at"
}

// NOTE for editors: never write a slash followed by a star inside a comment (for example when naming a folder with a wildcard).
// Kotlin block comments nest, so it opens a new comment that is never closed and the whole file fails to compile.

/**
 * Backup and restore of everything the shop owns: the Room database plus the receipt and product pictures that live next to it.
 *
 * A backup is one zip: manifest.json (app id, format, database version, time, counts, SHA-256 of the database), konoz_market.db
 * (a consistent snapshot taken with VACUUM INTO, or a checkpoint-and-copy on Android below 11), and the files of the
 * receipts and product_images folders.
 *
 * A restore REPLACES all data on the phone with the backup (it never merges), so no invoice can appear twice. Before anything is replaced the
 * file is verified (zip, manifest, same database version, SHA-256, SQLite integrity check, required tables, at least one active admin) and
 * the current data is saved as a before-restore zip. The database is closed during a restore: the app must be restarted afterwards.
 */
class BackupService(
    private val context: Context,
    private val db: KonozDatabase,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    // ---------- create ----------

    /** Writes a backup zip to [out] (closed afterwards). */
    suspend fun createBackup(out: OutputStream): BackupInfo = withContext(Dispatchers.IO) { writeBackup(out) }

    /** A scheduled backup: saved in the app's private backups folder; only the newest [KEEP_AUTO] are kept. */
    suspend fun createAutoBackup(): File = withContext(Dispatchers.IO) {
        val file = File(backupDir(), "auto-${stamp()}.zip")
        try {
            file.outputStream().use { writeBackup(it) }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        prune("auto-", KEEP_AUTO)
        recordBackupTime()
        file
    }

    suspend fun recordBackupTime() {
        db.settings().upsert(AppSetting(BackupSettings.LAST_AT, clock().toString()))
    }

    /** Backups kept on this phone (scheduled ones and the safety copies made before a restore), newest first. */
    fun listLocalBackups(): List<File> =
        backupDir().listFiles { f -> f.isFile && f.name.endsWith(".zip") }?.sortedByDescending { it.name } ?: emptyList()

    private fun writeBackup(out: OutputStream): BackupInfo {
        val work = freshWorkDir("create")
        try {
            val snapshot = File(work, DB_ENTRY)
            snapshotDatabase(snapshot)
            val receipts = mediaFiles("receipts")
            val images = mediaFiles("product_images")
            val info = BackupInfo(
                createdAt = clock(),
                appVersion = appVersionName(),
                schemaVersion = db.openHelper.readableDatabase.version,
                invoices = count("SELECT COUNT(*) FROM sales_invoices WHERE status = 'COMPLETED'"),
                products = count("SELECT COUNT(*) FROM products"),
                users = count("SELECT COUNT(*) FROM users"),
                receiptFiles = receipts.size,
                imageFiles = images.size,
            )
            val manifest = JSONObject()
                .put("app", APP_ID).put("format", FORMAT).put("createdAt", info.createdAt).put("appVersion", info.appVersion)
                .put("schemaVersion", info.schemaVersion).put("invoices", info.invoices).put("products", info.products)
                .put("users", info.users).put("dbSha256", sha256(snapshot))
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(DB_ENTRY))
                snapshot.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                for ((dirName, files) in listOf("receipts" to receipts, "product_images" to images)) {
                    for (f in files) {
                        try {
                            zip.putNextEntry(ZipEntry("$dirName/${f.name}"))
                            f.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        } catch (e: java.io.FileNotFoundException) {
                            // a picture deleted while the backup was running: it is not in the database any more either
                            zip.closeEntry()
                        }
                    }
                }
            }
            return info
        } finally {
            work.deleteRecursively()
        }
    }

    /** A consistent copy of the live database. VACUUM INTO needs SQLite 3.27 (Android 11); older phones checkpoint the WAL and copy the file. */
    private fun snapshotDatabase(into: File) {
        into.parentFile?.mkdirs()
        into.delete()
        val sdb = db.openHelper.writableDatabase
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                sdb.execSQL("VACUUM INTO '${into.absolutePath.replace("'", "''")}'")
                return
            } catch (e: Exception) {
                into.delete()
            }
        }
        sdb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        File(sdb.path!!).copyTo(into, overwrite = true)
    }

    // ---------- inspect ----------

    /** Verifies a backup file and returns what it contains, without changing anything. Throws [BackupException] if it is not usable. */
    suspend fun inspect(source: File): BackupInfo = withContext(Dispatchers.IO) {
        val work = freshWorkDir("inspect")
        try {
            extractAndValidate(source, work).first
        } finally {
            work.deleteRecursively()
        }
    }

    private fun extractAndValidate(source: File, work: File): Pair<BackupInfo, File> {
        val zip = try {
            ZipFile(source)
        } catch (e: Exception) {
            throw BackupException.NotZip()
        }
        return zip.use {
            val manifestEntry = zip.getEntry(MANIFEST) ?: throw BackupException.NotKonozBackup()
            val manifest = try {
                JSONObject(zip.getInputStream(manifestEntry).use { it.readBytes() }.toString(Charsets.UTF_8))
            } catch (e: Exception) {
                throw BackupException.NotKonozBackup()
            }
            if (manifest.optString("app") != APP_ID || manifest.optInt("format", -1) != FORMAT) throw BackupException.NotKonozBackup()
            val schema = manifest.optInt("schemaVersion", -1)
            val current = db.openHelper.readableDatabase.version
            if (schema != current) throw BackupException.IncompatibleVersion(schema, current)

            val dbEntry = zip.getEntry(DB_ENTRY) ?: throw BackupException.Corrupt()
            val dbFile = File(work, DB_ENTRY)
            try {
                zip.getInputStream(dbEntry).use { input -> dbFile.outputStream().use { input.copyTo(it) } }
            } catch (e: Exception) {
                throw BackupException.Corrupt()
            }
            if (sha256(dbFile) != manifest.optString("dbSha256")) throw BackupException.Corrupt()
            validateDatabase(dbFile, current)

            val info = BackupInfo(
                createdAt = manifest.optLong("createdAt"),
                appVersion = manifest.optString("appVersion"),
                schemaVersion = schema,
                invoices = manifest.optInt("invoices"),
                products = manifest.optInt("products"),
                users = manifest.optInt("users"),
                receiptFiles = zip.entries().asSequence().count { !it.isDirectory && it.name.startsWith("receipts/") },
                imageFiles = zip.entries().asSequence().count { !it.isDirectory && it.name.startsWith("product_images/") },
            )
            info to dbFile
        }
    }

    private fun validateDatabase(file: File, expectedVersion: Int) {
        val sq = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw BackupException.Corrupt()
        }
        try {
            val integrity = try {
                DatabaseUtils.stringForQuery(sq, "PRAGMA integrity_check", null)
            } catch (e: Exception) {
                throw BackupException.Corrupt()
            }
            if (integrity != "ok") throw BackupException.Corrupt()
            if (DatabaseUtils.longForQuery(sq, "PRAGMA user_version", null).toInt() != expectedVersion) throw BackupException.Corrupt()
            val tables = mutableSetOf<String>()
            sq.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { c ->
                while (c.moveToNext()) tables += c.getString(0)
            }
            if (!tables.containsAll(REQUIRED_TABLES)) throw BackupException.Corrupt()
            val admins = DatabaseUtils.longForQuery(sq, "SELECT COUNT(*) FROM users WHERE isActive = 1 AND role = 'ADMIN'", null)
            if (admins < 1L) throw BackupException.NoAdmin()
        } finally {
            sq.close()
        }
    }

    // ---------- restore ----------

    /**
     * Replaces ALL data on this phone with [source] (verified first). The current data is saved as a before-restore zip.
     * The database is closed afterwards, so the caller must restart the app. [actorUserId] is written to the restored audit log.
     */
    suspend fun restore(source: File, actorUserId: Long?): RestoreResult = withContext(Dispatchers.IO) {
        val work = freshWorkDir("restore")
        try {
            val (info, extractedDb) = extractAndValidate(source, work)
            File(backupDir(), "before-restore-${stamp()}.zip").let { safety ->
                try {
                    safety.outputStream().use { writeBackup(it) }
                } catch (e: Exception) {
                    safety.delete()
                    throw e
                }
            }
            prune("before-restore-", KEEP_SAFETY)

            val target = File(db.openHelper.writableDatabase.path!!)
            db.close()
            listOf(target, File(target.path + "-wal"), File(target.path + "-shm"), File(target.path + "-journal")).forEach { it.delete() }
            extractedDb.copyTo(target, overwrite = true)
            val autoEnabled = fixRestoredDatabase(target, actorUserId, info)
            restoreMedia(source)
            RestoreResult(info, autoEnabled)
        } finally {
            work.deleteRecursively()
        }
    }

    /**
     * Points picture paths at this phone's folders (they are stored as absolute paths and the folder can differ on another phone or user),
     * writes the restore into the audit log, and returns the daily-backup setting stored in the backup.
     */
    private fun fixRestoredDatabase(file: File, actorUserId: Long?, info: BackupInfo): Boolean {
        val sq = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            rewritePaths(sq, "products", "imagePath", "product_images")
            rewritePaths(sq, "expenses", "receiptPath", "receipts")
            sq.execSQL(
                "INSERT INTO audit_logs (userId, action, entity, entityId, details, createdAt) VALUES (?, 'BACKUP_RESTORED', 'backup', NULL, ?, ?)",
                arrayOf<Any?>(actorUserId, "backupCreatedAt=${info.createdAt} invoices=${info.invoices} products=${info.products}", clock()),
            )
            return try {
                DatabaseUtils.stringForQuery(sq, "SELECT value FROM app_settings WHERE `key` = ?", arrayOf(BackupSettings.AUTO_ENABLED)) == "true"
            } catch (e: android.database.sqlite.SQLiteDoneException) {
                false // stringForQuery throws when the setting does not exist
            }
        } finally {
            sq.close()
        }
    }

    private fun rewritePaths(sq: SQLiteDatabase, table: String, column: String, dirName: String) {
        val dir = File(context.filesDir, dirName)
        val rows = mutableListOf<Pair<Long, String>>()
        sq.rawQuery("SELECT id, $column FROM $table WHERE $column IS NOT NULL", null).use { c ->
            while (c.moveToNext()) rows += c.getLong(0) to c.getString(1)
        }
        for ((id, old) in rows) {
            val updated = File(dir, File(old).name).absolutePath
            if (updated != old) sq.execSQL("UPDATE $table SET $column = ? WHERE id = ?", arrayOf<Any?>(updated, id))
        }
    }

    /** Receipts and product pictures of the backup replace the ones on the phone. Entry names are reduced to a bare file name. */
    private fun restoreMedia(source: File) {
        ZipFile(source).use { zip ->
            for (dirName in MEDIA_DIRS) {
                val dir = File(context.filesDir, dirName)
                dir.deleteRecursively()
                dir.mkdirs()
                for (entry in zip.entries()) {
                    if (entry.isDirectory || !entry.name.startsWith("$dirName/")) continue
                    val name = File(entry.name).name
                    if (name.isEmpty() || name == "." || name == "..") continue
                    zip.getInputStream(entry).use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
                }
            }
        }
    }

    // ---------- helpers ----------
    private fun backupDir(): File = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }

    private fun mediaFiles(dirName: String): List<File> =
        File(context.filesDir, dirName).listFiles { f -> f.isFile }?.sortedBy { it.name } ?: emptyList()

    private fun prune(prefix: String, keep: Int) {
        backupDir().listFiles { f -> f.isFile && f.name.startsWith(prefix) }
            ?.sortedByDescending { it.name }?.drop(keep)?.forEach { it.delete() }
    }

    private fun freshWorkDir(name: String): File = File(context.cacheDir, "backup-$name").apply {
        deleteRecursively()
        mkdirs()
    }

    private fun count(sql: String): Int =
        db.openHelper.readableDatabase.query(sql).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun appVersionName(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(clock()))

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val APP_ID = "com.konoz.market"
        const val FORMAT = 1
        const val MANIFEST = "manifest.json"
        const val DB_ENTRY = "konoz_market.db"
        const val BACKUP_DIR = "backups"
        const val KEEP_AUTO = 7
        const val KEEP_SAFETY = 3
        private val MEDIA_DIRS = listOf("receipts", "product_images")
        private val REQUIRED_TABLES = setOf("products", "sales_invoices", "sales_items", "users", "inventory_movements", "app_settings", "audit_logs")
    }
}

package com.konoz.market.domain

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.konoz.market.data.KonozDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup file = one ZIP: manifest.json (first), konoz_market.db, then the files of the product_images and
 * receipts folders. (Do not write a slash followed by a star in a comment: Kotlin comments nest.)
 * Restore REPLACES all data with the file's content, so importing the same file twice gives the same state and
 * invoices can never be duplicated. The file is fully validated (format, safe names, SHA-256, SQLite integrity,
 * schema version not newer than this app) before anything is touched, and a safety backup of the current data is
 * kept first. After [restore] the caller must restart the app (the open database is closed).
 */
class BackupService(
    private val context: Context,
    private val db: KonozDatabase,
    private val dbName: String = KonozDatabase.FILE_NAME,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val dbFile: File get() = context.getDatabasePath(dbName)
    private val backupsDir: File get() = File(context.filesDir, BACKUPS_DIR)

    // ---------------- create ----------------

    /** Writes a complete backup to [out] (not closed). [actorId] is recorded in the audit log; null for the scheduler. */
    suspend fun createBackup(out: OutputStream, actorId: Long? = null): BackupManifest = withContext(Dispatchers.IO) {
        mutex.withLock { writeBackup(out, actorId, "backup_export") }
    }

    /** Saves a backup inside the app. Automatic ones are pruned to the newest [KEEP_AUTOMATIC]. */
    suspend fun createLocalBackup(automatic: Boolean, actorId: Long? = null): File = withContext(Dispatchers.IO) {
        mutex.withLock { writeLocal(if (automatic) "auto" else "manual", actorId, if (automatic) "backup_auto" else "backup_local") }
    }

    fun listLocalBackups(): List<LocalBackup> =
        (backupsDir.listFiles { f -> f.isFile && f.name.endsWith(EXTENSION) } ?: emptyArray())
            .map { LocalBackup(it.name, it.length(), it.lastModified(), it.name.contains("_auto_")) }
            .sortedByDescending { it.modifiedAt }

    fun localFile(name: String): File? =
        File(backupsDir, name).takeIf { '/' !in name && '\\' !in name && it.isFile }

    private fun writeLocal(kind: String, actorId: Long?, action: String): File {
        backupsDir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(clock()))
        val target = File(backupsDir, "konoz_${kind}_$stamp$EXTENSION")
        val tmp = File(backupsDir, target.name + ".tmp")
        try {
            FileOutputStream(tmp).use { writeBackup(it, actorId, action) }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("rename failed")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        if (kind == "auto") {
            listLocalBackups().filter { it.automatic }.drop(KEEP_AUTOMATIC).forEach { File(backupsDir, it.name).delete() }
        }
        return target
    }

    private fun writeBackup(out: OutputStream, actorId: Long?, action: String): BackupManifest {
        auditBlocking(actorId, action)
        val sdb = db.openHelper.writableDatabase
        val dbVersion = sdb.query("PRAGMA user_version").use { if (it.moveToFirst()) it.getInt(0) else 0 }
        val invoices = sdb.query("SELECT COUNT(*) FROM sales_invoices").use { if (it.moveToFirst()) it.getInt(0) else 0 }
        // Fold the write-ahead log into the main file, then hold the write lock while copying so nothing
        // (not even an automatic checkpoint) changes the file under us.
        sdb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val snapshot = File.createTempFile("snapshot", ".db", context.cacheDir)
        try {
            sdb.beginTransaction()
            try {
                dbFile.copyTo(snapshot, overwrite = true)
            } finally {
                sdb.endTransaction()
            }
            val images = filesIn(IMAGES_DIR)
            val receipts = filesIn(RECEIPTS_DIR)
            val manifest = BackupManifest(
                formatVersion = BackupManifest.FORMAT_VERSION, dbVersion = dbVersion, appVersion = appVersion(),
                createdAt = clock(), dbSha256 = sha256(snapshot),
                imageCount = images.size, receiptCount = receipts.size, salesInvoiceCount = invoices,
            )
            ZipOutputStream(out).let { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest.toJson().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry(DB_ENTRY))
                FileInputStream(snapshot).use { it.copyTo(zip) }
                zip.closeEntry()
                images.forEach { f -> zip.putNextEntry(ZipEntry("$IMAGES_DIR/${f.name}")); FileInputStream(f).use { it.copyTo(zip) }; zip.closeEntry() }
                receipts.forEach { f -> zip.putNextEntry(ZipEntry("$RECEIPTS_DIR/${f.name}")); FileInputStream(f).use { it.copyTo(zip) }; zip.closeEntry() }
                zip.finish() // does not close [out]
            }
            return manifest
        } finally {
            snapshot.delete()
        }
    }

    // ---------------- inspect / restore ----------------

    /** Validates a backup file without changing anything and returns what it holds. Throws [BackupException]. */
    suspend fun inspect(input: InputStream): BackupManifest = withContext(Dispatchers.IO) {
        mutex.withLock {
            val staged = stage(input)
            try { staged.manifest } finally { staged.dir.deleteRecursively() }
        }
    }

    /**
     * Replaces ALL data (database, product pictures, receipts) with the backup's. Throws [BackupException] before
     * touching anything if the file is invalid. The database is closed afterwards: restart the app.
     */
    suspend fun restore(input: InputStream, actorId: Long? = null): BackupManifest = withContext(Dispatchers.IO) {
        mutex.withLock {
            val staged = stage(input)
            try {
                writeLocal("pre_restore", actorId, "backup_pre_restore") // safety copy of what is about to be replaced
                prepareStagedDatabase(staged, actorId)
                db.close()
                swapDatabase(File(staged.dir, DB_ENTRY))
                replaceFolder(IMAGES_DIR, File(staged.dir, IMAGES_DIR))
                replaceFolder(RECEIPTS_DIR, File(staged.dir, RECEIPTS_DIR))
                staged.manifest
            } catch (e: BackupException) {
                throw e
            } catch (e: IOException) {
                throw BackupException(BackupFailure.IO, "restore failed: ${e.message}", e)
            } finally {
                staged.dir.deleteRecursively()
            }
        }
    }

    private class Staged(val dir: File, val manifest: BackupManifest)

    private fun stage(input: InputStream): Staged {
        val dir = File(context.cacheDir, "restore_${clock()}_${System.nanoTime()}").apply { mkdirs() }
        try {
            var manifest: BackupManifest? = null
            var first = true
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                var count = 0
                while (entry != null) {
                    if (++count > MAX_ENTRIES) throw BackupException(BackupFailure.CORRUPT, "too many entries")
                    val name = entry.name
                    if (first) {
                        if (name != MANIFEST) throw BackupException(BackupFailure.NOT_A_BACKUP, "manifest must be first")
                        val text = readLimited(zip, MAX_MANIFEST_BYTES)
                        if (text == null) throw BackupException(BackupFailure.NOT_A_BACKUP, "manifest too large")
                        val parsed = BackupManifest.fromJson(String(text, Charsets.UTF_8))
                            ?: throw BackupException(BackupFailure.NOT_A_BACKUP, "bad manifest")
                        if (parsed.formatVersion > BackupManifest.FORMAT_VERSION) throw BackupException(BackupFailure.NEWER_VERSION, "newer format")
                        if (parsed.dbVersion > currentDbVersion()) throw BackupException(BackupFailure.NEWER_VERSION, "newer database")
                        manifest = parsed
                        first = false
                    } else if (!entry.isDirectory) {
                        val target = targetFor(dir, name) ?: throw BackupException(BackupFailure.CORRUPT, "unsafe entry")
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { zip.copyTo(it) }
                    }
                    entry = zip.nextEntry
                }
            }
            val m = manifest ?: throw BackupException(BackupFailure.NOT_A_BACKUP, "empty file")
            val dbCopy = File(dir, DB_ENTRY)
            if (!dbCopy.isFile) throw BackupException(BackupFailure.CORRUPT, "database missing")
            if (sha256(dbCopy) != m.dbSha256) throw BackupException(BackupFailure.CORRUPT, "checksum mismatch")
            verifyDatabase(dbCopy, m)
            return Staged(dir, m)
        } catch (e: BackupException) {
            dir.deleteRecursively(); throw e
        } catch (e: ZipException) {
            dir.deleteRecursively(); throw BackupException(BackupFailure.NOT_A_BACKUP, "not a zip", e)
        } catch (e: IOException) {
            dir.deleteRecursively(); throw BackupException(BackupFailure.IO, "read failed", e)
        }
    }

    /** Reads at most [limit] bytes; null when the stream has more. (readNBytes needs API 33.) */
    private fun readLimited(input: InputStream, limit: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > limit) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Only the database and flat files inside the two known folders are accepted: no paths, no dot-dot. */
    private fun targetFor(root: File, name: String): File? {
        if (name == DB_ENTRY) return File(root, DB_ENTRY)
        val parts = name.split('/')
        if (parts.size != 2 || parts[0] !in setOf(IMAGES_DIR, RECEIPTS_DIR)) return null
        val file = parts[1]
        if (file.isEmpty() || file.startsWith(".") || '\\' in file || file.contains("..")) return null
        return File(File(root, parts[0]), file)
    }

    private fun verifyDatabase(file: File, manifest: BackupManifest) {
        val sqlite = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            throw BackupException(BackupFailure.CORRUPT, "cannot open database", e)
        }
        sqlite.use {
            val ok = it.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" }
            if (!ok) throw BackupException(BackupFailure.CORRUPT, "integrity check failed")
            val version = it.rawQuery("PRAGMA user_version", null).use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }
            if (version != manifest.dbVersion) throw BackupException(BackupFailure.CORRUPT, "version mismatch")
            val hasInvoices = it.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name='sales_invoices'", null).use { c -> c.moveToFirst() }
            if (!hasInvoices) throw BackupException(BackupFailure.NOT_A_BACKUP, "not a Konoz database")
        }
    }

    /** Points picture paths at this phone's folders (the absolute path can differ) and records the restore in the audit log. */
    private fun prepareStagedDatabase(staged: Staged, actorId: Long?) {
        val file = File(staged.dir, DB_ENTRY)
        val sqlite = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        sqlite.use {
            it.beginTransaction()
            try {
                rewritePaths(it, "products", "imagePath", File(context.filesDir, IMAGES_DIR))
                rewritePaths(it, "expenses", "receiptPath", File(context.filesDir, RECEIPTS_DIR))
                it.execSQL(
                    "INSERT INTO audit_logs(userId, action, entity, entityId, details, createdAt) VALUES(NULL, ?, 'backup', NULL, ?, ?)",
                    arrayOf<Any?>("backup_restore", "restoredBy=${actorId ?: 0}; backupCreatedAt=${staged.manifest.createdAt}", clock()),
                )
                it.setTransactionSuccessful()
            } finally {
                it.endTransaction()
            }
            it.rawQuery("PRAGMA journal_mode=DELETE", null).use { c -> c.moveToFirst() }
        }
    }

    private fun rewritePaths(sqlite: SQLiteDatabase, table: String, column: String, dir: File) {
        val rows = sqlite.rawQuery("SELECT rowid, $column FROM $table WHERE $column IS NOT NULL", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to c.getString(1)) }
        }
        rows.forEach { (rowId, old) ->
            sqlite.execSQL("UPDATE $table SET $column = ? WHERE rowid = ?", arrayOf<Any?>(File(dir, File(old).name).path, rowId))
        }
    }

    private fun swapDatabase(staged: File) {
        val target = dbFile
        val incoming = File(target.parentFile, target.name + ".restore")
        staged.copyTo(incoming, overwrite = true)
        listOf("-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
        target.delete()
        if (!incoming.renameTo(target)) throw IOException("could not replace the database")
    }

    private fun replaceFolder(name: String, source: File) {
        val dest = File(context.filesDir, name).apply { mkdirs() }
        dest.listFiles()?.forEach { it.delete() }
        source.listFiles()?.forEach { it.copyTo(File(dest, it.name), overwrite = true) }
    }

    // ---------------- helpers ----------------

    private fun currentDbVersion(): Int =
        db.openHelper.readableDatabase.query("PRAGMA user_version").use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun filesIn(folder: String): List<File> =
        (File(context.filesDir, folder).listFiles { f -> f.isFile } ?: emptyArray()).sortedBy { it.name }

    private fun appVersion(): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun auditBlocking(actorId: Long?, action: String) {
        val sdb = db.openHelper.writableDatabase
        sdb.execSQL(
            "INSERT INTO audit_logs(userId, action, entity, entityId, details, createdAt) VALUES(?, ?, 'backup', NULL, NULL, ?)",
            arrayOf<Any?>(actorId, action, clock()),
        )
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val EXTENSION = ".konozbackup"
        const val KEEP_AUTOMATIC = 7
        private const val BACKUPS_DIR = "backups"
        const val IMAGES_DIR = "product_images"
        const val RECEIPTS_DIR = "receipts"
        private const val MANIFEST = "manifest.json"
        private const val DB_ENTRY = "konoz_market.db"
        private const val MAX_ENTRIES = 200_000
        private const val MAX_MANIFEST_BYTES = 64 * 1024
    }
}

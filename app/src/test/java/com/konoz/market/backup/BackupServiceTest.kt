package com.konoz.market.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.Expense
import com.konoz.market.data.entity.ExpenseCategory
import com.konoz.market.data.entity.Product
import com.konoz.market.data.seed.DatabaseInitializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupServiceTest {

    private lateinit var ctx: Context
    private lateinit var db: KonozDatabase
    private lateinit var service: BackupService
    private val opened = mutableListOf<KonozDatabase>()
    private val now = 1_700_000_000_000L

    private fun openDb(): KonozDatabase =
        Room.databaseBuilder(ctx, KonozDatabase::class.java, DB_NAME).allowMainThreadQueries().build().also { opened += it }

    @Before fun setUp() = runBlocking {
        ctx = ApplicationProvider.getApplicationContext()
        cleanFiles()
        db = openDb()
        service = BackupService(ctx, db, { now })
        DatabaseInitializer.seedIfNeeded(db, now) // an active admin
    }

    @After fun tearDown() {
        opened.forEach { it.close() }
        cleanFiles()
    }

    private fun cleanFiles() {
        ctx.deleteDatabase(DB_NAME)
        listOf("receipts", "product_images", "backups").forEach { File(ctx.filesDir, it).deleteRecursively() }
    }

    private fun media(dir: String, name: String, bytes: ByteArray): File =
        File(ctx.filesDir, dir).apply { mkdirs() }.let { File(it, name).apply { writeBytes(bytes) } }

    private suspend fun backupFile(name: String = "backup.zip"): File =
        File(ctx.cacheDir, name).also { f -> f.outputStream().use { service.createBackup(it) } }

    /** Copies a zip, letting [transform] change or drop each entry (return null to drop it). */
    private fun rewrite(src: File, dst: File, transform: (String, ByteArray) -> ByteArray?): File {
        ZipFile(src).use { zip ->
            ZipOutputStream(dst.outputStream()).use { out ->
                for (e in zip.entries()) {
                    val bytes = zip.getInputStream(e).use { it.readBytes() }
                    val changed = transform(e.name, bytes) ?: continue
                    out.putNextEntry(ZipEntry(e.name))
                    out.write(changed)
                    out.closeEntry()
                }
            }
        }
        return dst
    }

    private suspend fun names(database: KonozDatabase) = database.products().getAllActive().map { it.name }.sorted()

    // ---------- create and inspect ----------
    @Test fun a_backup_describes_what_it_contains() = runBlocking {
        db.products().insert(Product(name = "Milk", createdAt = now))
        media("receipts", "r1.jpg", byteArrayOf(1, 2, 3))
        media("product_images", "p1.jpg", byteArrayOf(4, 5))

        val info = service.inspect(backupFile())

        assertEquals(now, info.createdAt)
        assertEquals(1, info.products)
        assertEquals(1, info.users) // the admin
        assertEquals(0, info.invoices)
        assertEquals(1, info.receiptFiles)
        assertEquals(1, info.imageFiles)
        assertEquals(db.openHelper.readableDatabase.version, info.schemaVersion)
    }

    @Test fun a_scheduled_backup_is_saved_on_the_phone_and_only_the_newest_seven_are_kept() = runBlocking {
        var t = now
        val timed = BackupService(ctx, db, { t })
        repeat(9) {
            timed.createAutoBackup()
            t += 1_000
        }

        val kept = timed.listLocalBackups()

        assertEquals(BackupService.KEEP_AUTO, kept.size)
        assertTrue(kept.all { it.name.startsWith("auto-") })
        assertEquals(t - 1_000, db.settings().get(BackupSettings.LAST_AT)!!.toLong())
    }

    // ---------- restore ----------
    @Test fun restore_returns_the_phone_to_the_state_of_the_backup() = runBlocking {
        db.products().insert(Product(name = "A", createdAt = now))
        media("receipts", "r1.jpg", byteArrayOf(1, 2, 3))
        val backup = backupFile()
        // changes made after the backup
        db.products().insert(Product(name = "B", createdAt = now))
        File(ctx.filesDir, "receipts/r1.jpg").delete()
        media("receipts", "extra.jpg", byteArrayOf(9))

        val result = service.restore(backup, actorUserId = null)

        assertEquals(1, result.info.products)
        val reopened = openDb()
        assertEquals(listOf("A"), names(reopened))
        assertArrayEquals(byteArrayOf(1, 2, 3), File(ctx.filesDir, "receipts/r1.jpg").readBytes())
        assertFalse(File(ctx.filesDir, "receipts/extra.jpg").exists())
        assertTrue("the current data is kept as a safety copy", service.listLocalBackups().any { it.name.startsWith("before-restore-") })
        assertTrue(reopened.audit().observeRecent(20).first().any { it.action == "BACKUP_RESTORED" })
    }

    @Test fun restoring_the_same_backup_twice_does_not_duplicate_anything() = runBlocking {
        db.products().insert(Product(name = "A", createdAt = now))
        val backup = backupFile()
        service.restore(backup, null)

        val reopened = openDb()
        BackupService(ctx, reopened, { now + 1_000 }).restore(backup, null)

        assertEquals(listOf("A"), names(openDb()))
    }

    @Test fun picture_paths_are_pointed_at_this_phones_folders() = runBlocking {
        val userId = db.users().findActiveByUsername(DatabaseInitializer.DEFAULT_ADMIN_USERNAME)!!.id
        val productId = db.products().insert(Product(name = "A", imagePath = "/data/user/10/old/files/product_images/p1.jpg", createdAt = now))
        val expenseId = db.expenses().insert(
            Expense(
                uuid = "exp-1", amount = 500, category = ExpenseCategory.MISC, description = "x",
                receiptPath = "/somewhere/else/receipts/r1.jpg", userId = userId, createdAt = now,
            )
        )
        val backup = backupFile()

        service.restore(backup, null)

        val reopened = openDb()
        assertEquals(File(ctx.filesDir, "product_images/p1.jpg").absolutePath, reopened.products().getById(productId)!!.imagePath)
        assertEquals(File(ctx.filesDir, "receipts/r1.jpg").absolutePath, reopened.expenses().getById(expenseId)!!.receiptPath)
    }

    @Test fun a_backup_that_is_not_usable_changes_nothing() = runBlocking {
        db.products().insert(Product(name = "A", createdAt = now))
        val junk = File(ctx.cacheDir, "junk.zip").apply { writeText("not a zip") }

        assertThrows(BackupException.NotZip::class.java) { runBlocking { service.restore(junk, null) } }

        assertEquals(listOf("A"), names(db)) // the database is still open and intact
        assertTrue(service.listLocalBackups().none { it.name.startsWith("before-restore-") })
    }

    // ---------- verification ----------
    @Test fun a_file_that_is_not_a_zip_is_rejected() {
        val junk = File(ctx.cacheDir, "junk.txt").apply { writeText("hello") }

        assertThrows(BackupException.NotZip::class.java) { runBlocking { service.inspect(junk) } }
    }

    @Test fun a_zip_from_another_app_or_without_a_manifest_is_rejected() = runBlocking {
        val good = backupFile()
        val foreign = rewrite(good, File(ctx.cacheDir, "foreign.zip")) { name, bytes ->
            if (name == BackupService.MANIFEST) JSONObject(String(bytes)).put("app", "com.other.app").toString().toByteArray() else bytes
        }
        val noManifest = rewrite(good, File(ctx.cacheDir, "nomanifest.zip")) { name, bytes -> if (name == BackupService.MANIFEST) null else bytes }

        assertThrows(BackupException.NotKonozBackup::class.java) { runBlocking { service.inspect(foreign) } }
        assertThrows(BackupException.NotKonozBackup::class.java) { runBlocking { service.inspect(noManifest) } }
    }

    @Test fun a_backup_from_a_different_database_version_is_rejected() = runBlocking {
        val newer = rewrite(backupFile(), File(ctx.cacheDir, "newer.zip")) { name, bytes ->
            if (name == BackupService.MANIFEST) JSONObject(String(bytes)).put("schemaVersion", 99).toString().toByteArray() else bytes
        }

        val ex = assertThrows(BackupException.IncompatibleVersion::class.java) { runBlocking { service.inspect(newer) } }

        assertEquals(99, ex.backupVersion)
    }

    @Test fun a_tampered_or_truncated_database_is_rejected() = runBlocking {
        val tampered = rewrite(backupFile(), File(ctx.cacheDir, "tampered.zip")) { name, bytes ->
            if (name == BackupService.DB_ENTRY) bytes + byteArrayOf(0x42) else bytes
        }
        val noDatabase = rewrite(backupFile("b2.zip"), File(ctx.cacheDir, "nodb.zip")) { name, bytes -> if (name == BackupService.DB_ENTRY) null else bytes }

        assertThrows(BackupException.Corrupt::class.java) { runBlocking { service.inspect(tampered) } }
        assertThrows(BackupException.Corrupt::class.java) { runBlocking { service.inspect(noDatabase) } }
    }

    @Test fun a_backup_without_an_active_admin_is_rejected() = runBlocking {
        val admin = db.users().findActiveByUsername(DatabaseInitializer.DEFAULT_ADMIN_USERNAME)!!
        db.users().update(admin.copy(isActive = false))

        assertThrows(BackupException.NoAdmin::class.java) { runBlocking { service.inspect(backupFile()) } }
    }

    private companion object {
        const val DB_NAME = "backup-test.db"
    }
}

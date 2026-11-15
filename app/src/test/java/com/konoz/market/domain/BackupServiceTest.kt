package com.konoz.market.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupServiceTest {
    private lateinit var ctx: Context
    private lateinit var db: KonozDatabase
    private lateinit var service: BackupService
    private var now = 1_700_000_000_000L
    private val dbName = "backup_test.db"

    private fun open() = Room.databaseBuilder(ctx, KonozDatabase::class.java, dbName).allowMainThreadQueries().build()

    private fun addUser(name: String) = runBlocking {
        db.users().insert(User(name = name, username = name, pinHash = "h", salt = "s", role = UserRole.ADMIN, createdAt = now))
    }

    private fun userNames(): List<String> = runBlocking {
        db.openHelper.readableDatabase.query("SELECT username FROM users ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
    }

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase(dbName)
        File(ctx.filesDir, "product_images").deleteRecursively()
        File(ctx.filesDir, "receipts").deleteRecursively()
        File(ctx.filesDir, "backups").deleteRecursively()
        db = open()
        service = BackupService(ctx, db, dbName, { now })
    }

    @After fun tearDown() {
        if (db.isOpen) db.close()
    }

    private fun backupBytes(): ByteArray = ByteArrayOutputStream().also { out -> runBlocking { service.createBackup(out) } }.toByteArray()

    @Test fun backupHoldsDataAndFiles() {
        addUser("first")
        File(ctx.filesDir, "receipts").apply { mkdirs() }.let { File(it, "r1.jpg").writeText("receipt") }
        val manifest = runBlocking { service.inspect(ByteArrayInputStream(backupBytes())) }
        assertEquals(1, manifest.receiptCount)
        assertEquals(0, manifest.imageCount)
        assertEquals(BackupManifest.FORMAT_VERSION, manifest.formatVersion)
    }

    @Test fun restoreReplacesDataAndFiles() {
        addUser("first")
        File(ctx.filesDir, "receipts").apply { mkdirs() }.let { File(it, "r1.jpg").writeText("old") }
        val bytes = backupBytes()
        addUser("second")
        File(ctx.filesDir, "receipts/extra.jpg").writeText("extra")

        runBlocking { service.restore(ByteArrayInputStream(bytes)) }
        db = open()
        assertEquals(listOf("first"), userNames().filter { it == "first" || it == "second" })
        assertEquals("old", File(ctx.filesDir, "receipts/r1.jpg").readText())
        assertTrue(!File(ctx.filesDir, "receipts/extra.jpg").exists())
    }

    @Test fun restoreTwiceGivesSameStateAndKeepsSafetyCopy() {
        addUser("first")
        val bytes = backupBytes()
        runBlocking { service.restore(ByteArrayInputStream(bytes)) }
        db = open()
        service = BackupService(ctx, db, dbName, { now })
        runBlocking { service.restore(ByteArrayInputStream(bytes)) }
        db = open()
        assertEquals(1, userNames().count { it == "first" })
        assertTrue(File(ctx.filesDir, "backups").listFiles()!!.any { it.name.contains("pre_restore") })
    }

    @Test fun garbageIsRejectedAndNothingChanges() {
        addUser("first")
        try {
            runBlocking { service.restore(ByteArrayInputStream("hello".toByteArray())) }
            fail()
        } catch (e: BackupException) {
            assertEquals(BackupFailure.NOT_A_BACKUP, e.failure)
        }
        assertTrue(db.isOpen)
        assertEquals(listOf("first"), userNames())
    }

    @Test fun tamperedDatabaseFailsChecksum() {
        addUser("first")
        val original = backupBytes()
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            java.util.zip.ZipInputStream(ByteArrayInputStream(original)).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    zip.putNextEntry(ZipEntry(e.name))
                    val data = zin.readBytes()
                    if (e.name == "konoz_market.db") data[data.size - 1] = (data[data.size - 1] + 1).toByte()
                    zip.write(data); zip.closeEntry()
                    e = zin.nextEntry
                }
            }
        }
        try {
            runBlocking { service.restore(ByteArrayInputStream(out.toByteArray())) }
            fail()
        } catch (e: BackupException) {
            assertEquals(BackupFailure.CORRUPT, e.failure)
        }
        assertTrue(db.isOpen)
    }

    @Test fun pathTraversalEntryIsRejected() {
        val manifest = BackupManifest(1, 1, "", now, "x", 0, 0, 0)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toJson().toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("receipts/../../evil.txt")); zip.write(1); zip.closeEntry()
        }
        try {
            runBlocking { service.inspect(ByteArrayInputStream(out.toByteArray())) }
            fail()
        } catch (e: BackupException) {
            assertEquals(BackupFailure.CORRUPT, e.failure)
        }
    }

    @Test fun automaticBackupsKeepOnlyTheNewest() {
        repeat(BackupService.KEEP_AUTOMATIC + 3) {
            now += 1000L * 60 * 60 * 24
            runBlocking { service.createLocalBackup(automatic = true) }
        }
        assertEquals(BackupService.KEEP_AUTOMATIC, service.listLocalBackups().count { it.automatic })
    }
}

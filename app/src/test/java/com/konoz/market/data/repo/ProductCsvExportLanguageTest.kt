package com.konoz.market.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.core.Csv
import com.konoz.market.data.KonozDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductCsvExportLanguageTest {

    private val now = 1_700_000_000_000L
    private lateinit var db: KonozDatabase
    private lateinit var repo: ProductRepository
    private lateinit var csv: ProductCsvService

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
        repo = ProductRepository(db) { now }
        csv = ProductCsvService(db, repo)
    }

    @After fun tearDown() = db.close()

    private suspend fun seed() {
        repo.save(ProductInput(name = "Milk", internalCode = "M1", costPrice = 1000, salePrice = 1500, openingQtyMilli = 5000), null)
    }

    @Test fun english_export_uses_english_headers_and_unit_names() = runBlocking {
        seed()
        val rows = Csv.parse(csv.exportCsv(CsvLanguage.EN))
        assertEquals(ProductCsvService.HEADERS, rows[0])
        assertEquals("PIECE", rows[1][3])
    }

    @Test fun arabic_export_uses_arabic_headers_and_unit_names() = runBlocking {
        seed()
        val rows = Csv.parse(csv.exportCsv(CsvLanguage.AR))
        assertEquals(ProductCsvService.EXPORT_HEADERS, rows[0])
        assertEquals("قطعة", rows[1][3])
    }

    @Test fun both_exports_import_back_to_the_same_product() = runBlocking {
        seed()
        for (language in CsvLanguage.values()) {
            val text = csv.exportCsv(language)
            val result = csv.importCsv(text, null)
            assertEquals(0, result.created)
            assertEquals(1, result.updated)
            assertEquals(emptyList<ImportError>(), result.errors)
        }
    }
}

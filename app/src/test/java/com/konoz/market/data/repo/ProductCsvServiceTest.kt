package com.konoz.market.data.repo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.konoz.market.core.Csv
import com.konoz.market.data.KonozDatabase
import com.konoz.market.data.entity.Category
import com.konoz.market.data.entity.UnitType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductCsvServiceTest {

    private val now = 1_700_000_000_000L
    private lateinit var db: KonozDatabase
    private lateinit var repo: ProductRepository
    private lateinit var csv: ProductCsvService

    private fun newDb(): KonozDatabase {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(ctx, KonozDatabase::class.java).allowMainThreadQueries().build()
    }

    @Before fun setUp() {
        db = newDb()
        repo = ProductRepository(db) { now }
        csv = ProductCsvService(db, repo)
    }

    @After fun tearDown() = db.close()

    private suspend fun seedMilk(): Long {
        val cat = db.categories().insert(Category(name = "Dairy"))
        return repo.save(
            ProductInput(
                name = "Milk", internalCode = "M1", categoryId = cat, costPrice = 1000, salePrice = 1500,
                barcodes = listOf(BarcodeInput("111"), BarcodeInput("CARTON", 12)), openingQtyMilli = 5000,
            ),
            null,
        )
    }

    @Test fun export_has_the_header_and_one_row_per_product() = runBlocking {
        seedMilk()
        val rows = Csv.parse(csv.exportCsv())
        assertEquals(ProductCsvService.HEADERS, rows[0])
        assertEquals(
            listOf("Milk", "M1", "Dairy", "PIECE", "10.00", "15.00", "0", "", "111|CARTON*12", "5", "", "1"),
            rows[1],
        )
        assertEquals(2, rows.size)
    }

    @Test fun export_then_import_into_an_empty_database_recreates_the_product() = runBlocking {
        seedMilk()
        val text = csv.exportCsv()

        val db2 = newDb()
        try {
            val repo2 = ProductRepository(db2) { now }
            val result = ProductCsvService(db2, repo2).importCsv(text, null)
            assertEquals(1, result.created)
            assertEquals(0, result.errors.size)

            val p = db2.products().findByInternalCode("M1")
            assertNotNull(p)
            assertEquals(1500L, p!!.salePrice)
            assertEquals(1000L, p.costPrice)
            assertEquals(5000L, p.currentQtyMilli)
            assertEquals(UnitType.PIECE, p.unit)
            assertEquals("Dairy", db2.categories().findByName("Dairy")?.name)
            assertEquals(listOf("111" to 1, "CARTON" to 12), db2.barcodes().getForProduct(p.id).map { it.code to it.unitsPerScan })
        } finally {
            db2.close()
        }
    }

    @Test fun importing_an_existing_product_updates_it_but_never_touches_its_stock() = runBlocking {
        val id = seedMilk()
        val text = "name,internal_code,sale_price,quantity\nMilk Renamed,M1,20,999\n"

        val result = csv.importCsv(text, null)

        assertEquals(0, result.created)
        assertEquals(1, result.updated)
        val p = db.products().getById(id)!!
        assertEquals("Milk Renamed", p.name)
        assertEquals(2000L, p.salePrice)
        assertEquals(5000L, p.currentQtyMilli) // quantity column is ignored for existing products
        assertEquals(2, db.barcodes().getForProduct(id).size) // barcodes kept when the column is empty
    }

    @Test fun a_bad_row_is_reported_with_its_line_number_and_the_others_still_import() = runBlocking {
        val text = "name,sale_price\nGood A,10\nBad,abc\nGood B,5\n"
        val result = csv.importCsv(text, null)
        assertEquals(2, result.created)
        assertEquals(listOf(3), result.errors.map { it.line })
        assertEquals(2, db.products().count())
    }

    @Test fun semicolon_separated_arabic_files_with_decimal_commas_are_accepted() = runBlocking {
        val text = "name;sale_price\nحليب;12,5\n"
        val result = csv.importCsv(text, null)
        assertEquals(1, result.created)
        assertEquals(1250L, db.products().observeAll().first().single().salePrice)
    }

    @Test fun a_file_without_a_name_column_is_rejected() = runBlocking {
        val result = csv.importCsv("title,price\nx,1\n", null)
        assertEquals(0, result.created)
        assertEquals(1, result.errors.size)
    }
}

package com.konoz.market.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManifestTest {
    private val sample = BackupManifest(
        formatVersion = 1, dbVersion = 2, appVersion = "0.1.0", createdAt = 1_700_000_000_000L,
        dbSha256 = "abc", imageCount = 3, receiptCount = 2, salesInvoiceCount = 40,
    )

    @Test fun roundTrip() = assertEquals(sample, BackupManifest.fromJson(sample.toJson()))

    @Test fun rejectsOtherApps() = assertNull(BackupManifest.fromJson(sample.toJson().replace("com.konoz.market", "other.app")))

    @Test fun rejectsGarbage() {
        assertNull(BackupManifest.fromJson("not json"))
        assertNull(BackupManifest.fromJson("{}"))
    }
}

package com.konoz.market.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EscPosTest {
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test fun packsEightPixelsPerByteMostSignificantFirst() {
        val px = IntArray(16) { white }
        px[0] = black; px[7] = black; px[9] = black
        val out = EscPos.raster(px, 16, 1)
        assertArrayEquals(byteArrayOf(0x1D, 0x76, 0x30, 0x00, 2, 0, 1, 0, 0x81.toByte(), 0x40), out)
    }

    @Test fun transparentCountsAsWhiteAndLightGreyIsWhite() {
        val px = IntArray(8) { 0x00000000 }
        px[0] = 0xFFC8C8C8.toInt()
        px[1] = 0xFF303030.toInt()
        val out = EscPos.raster(px, 8, 1)
        assertEquals(0x40, out.last().toInt() and 0xFF)
    }

    @Test fun tallImagesAreSplitIntoBands() {
        val h = 300
        val out = EscPos.raster(IntArray(8 * h) { white }, 8, h)
        // 128 + 128 + 44 rows: three headers of 8 bytes each plus one data byte per row.
        assertEquals(3 * 8 + h, out.size)
        assertEquals(128, out[6].toInt() and 0xFF)
        assertEquals(44, out[8 + 128 + 8 + 128 + 6].toInt() and 0xFF)
    }

    @Test fun rejectsBadSizes() {
        assertThrows(IllegalArgumentException::class.java) { EscPos.raster(IntArray(10), 10, 1) }
        assertThrows(IllegalArgumentException::class.java) { EscPos.raster(IntArray(7), 8, 1) }
    }

    @Test fun jobStartsWithResetAndEndsWithCut() {
        val out = EscPos.job(IntArray(8) { white }, 8, 1)
        assertArrayEquals(EscPos.initialize(), out.copyOfRange(0, 2))
        assertArrayEquals(EscPos.cut(), out.copyOfRange(out.size - 4, out.size))
    }
}

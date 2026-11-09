package com.konoz.market.ui.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDebouncerTest {

    @Test fun first_scan_is_accepted() {
        assertTrue(ScanDebouncer().accept("111", 0))
    }

    @Test fun repeated_frames_of_the_same_code_are_ignored_then_allowed_after_the_window() {
        val d = ScanDebouncer(sameCodeWindowMs = 1500, anyCodeWindowMs = 700)
        assertTrue(d.accept("111", 1_000))
        assertFalse(d.accept("111", 1_100))
        assertFalse(d.accept("111", 2_400))
        assertTrue(d.accept("111", 2_600)) // 1.6 s later: the cashier scans a second unit
    }

    @Test fun a_different_code_is_accepted_after_the_short_window() {
        val d = ScanDebouncer(sameCodeWindowMs = 1500, anyCodeWindowMs = 700)
        assertTrue(d.accept("111", 1_000))
        assertFalse(d.accept("222", 1_300)) // too soon after any scan
        assertTrue(d.accept("222", 1_800))
    }
}

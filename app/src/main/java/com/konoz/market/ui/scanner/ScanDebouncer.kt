package com.konoz.market.ui.scanner

/**
 * Stops the camera from reporting the same barcode dozens of times per second.
 *  - any code: ignored if another one was accepted less than [anyCodeWindowMs] ago
 *  - same code: ignored for [sameCodeWindowMs] (the cashier scans it again after that to add one more)
 */
class ScanDebouncer(
    private val sameCodeWindowMs: Long = 1500,
    private val anyCodeWindowMs: Long = 700,
) {
    private var lastCode: String? = null
    private var lastAt = Long.MIN_VALUE / 2

    fun accept(code: String, nowMs: Long): Boolean {
        val elapsed = nowMs - lastAt
        if (elapsed < anyCodeWindowMs) return false
        if (code == lastCode && elapsed < sameCodeWindowMs) return false
        lastCode = code
        lastAt = nowMs
        return true
    }
}

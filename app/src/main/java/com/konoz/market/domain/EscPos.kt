package com.konoz.market.domain

/**
 * ESC/POS bytes for receipt printers. Arabic text is NOT sent as text (printers lack the glyphs and shaping):
 * the invoice is drawn as a picture and sent as a raster image, so it prints exactly like the PDF.
 */
object EscPos {
    /** Printable width in dots at 203 dpi. */
    const val DOTS_58 = 384
    const val DOTS_80 = 576

    private const val BAND_ROWS = 128

    fun initialize(): ByteArray = byteArrayOf(0x1B, 0x40)

    fun feed(lines: Int): ByteArray = byteArrayOf(0x1B, 0x64, lines.coerceIn(0, 255).toByte())

    /** Partial cut (printers without a cutter ignore it). */
    fun cut(): ByteArray = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    /**
     * Converts ARGB [pixels] (row by row, [width] x [height]) to GS v 0 raster commands. Dark pixels print black;
     * transparent ones count as white. [width] must be a multiple of 8.
     */
    fun raster(pixels: IntArray, width: Int, height: Int): ByteArray {
        require(width > 0 && width % 8 == 0) { "width must be a positive multiple of 8" }
        require(height >= 0 && pixels.size == width * height) { "pixel count does not match size" }
        val bytesPerRow = width / 8
        val out = java.io.ByteArrayOutputStream()
        var row = 0
        while (row < height) {
            val rows = minOf(BAND_ROWS, height - row)
            out.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))
            out.write(bytesPerRow and 0xFF); out.write(bytesPerRow shr 8)
            out.write(rows and 0xFF); out.write(rows shr 8)
            for (r in row until row + rows) {
                for (b in 0 until bytesPerRow) {
                    var v = 0
                    for (bit in 0 until 8) {
                        if (isDark(pixels[r * width + b * 8 + bit])) v = v or (0x80 shr bit)
                    }
                    out.write(v)
                }
            }
            row += rows
        }
        return out.toByteArray()
    }

    /** A complete print job: reset, picture, feed, cut. */
    fun job(pixels: IntArray, width: Int, height: Int): ByteArray =
        initialize() + raster(pixels, width, height) + feed(4) + cut()

    private fun isDark(argb: Int): Boolean {
        val a = argb ushr 24
        if (a < 128) return false
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000 < 128
    }
}

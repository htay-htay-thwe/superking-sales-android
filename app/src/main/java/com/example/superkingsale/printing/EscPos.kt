package com.example.superkingsale.printing

/** ESC/POS GS v 0 raster; common legacy receipt printers. No text-codepage dependence. */
object EscPos {
    val initialize = byteArrayOf(0x1b, 0x40)
    val feed = byteArrayOf(0x1b, 0x64, 3)
    val cut = byteArrayOf(0x1d, 0x56, 1)
    fun raster(width: Int, height: Int, black: (Int, Int) -> Boolean): ByteArray {
        require(width in 8..832 && width % 8 == 0 && height in 1..256)
        val stride = width / 8
        val output = ByteArray(8 + stride * height)
        byteArrayOf(0x1d, 0x76, 0x30, 0, stride.toByte(), (stride shr 8).toByte(), height.toByte(), (height shr 8).toByte()).copyInto(output)
        for (y in 0 until height) for (x in 0 until width) if (black(x, y)) {
            val index = 8 + y * stride + x / 8
            output[index] = (output[index].toInt() or (0x80 shr (x % 8))).toByte()
        }
        return output
    }
    fun defaultDots(paper: Int) = when (paper) { 50 -> 360; 58 -> 384; 80 -> 576; else -> error("Unsupported paper width") }
    fun pagePoints(mm: Int) = kotlin.math.round(mm * 72.0 / 25.4).toInt()
}

package com.example.superkingsale

import com.example.superkingsale.location.FixQuality
import com.example.superkingsale.printing.EscPos
import org.junit.Assert.*
import org.junit.Test

class HardwareRulesTest {
    @Test fun locationRejectsStaleFutureInvalidAndMissingAccuracy() {
        val now = 200_000_000_000L
        assertTrue(FixQuality.usable(21.0, 96.0, 12f, now - 10, now))
        assertTrue(FixQuality.usable(21.0, 96.0, 2000f, now - 10, now)) // approximation requires UI consent, not a hidden business restriction
        assertTrue(FixQuality.usable(21.0, 96.0, 12f, now - 119_000_000_000, now))
        assertFalse(FixQuality.usable(21.0, 96.0, 12f, now - 121_000_000_000, now))
        assertFalse(FixQuality.usable(21.0, 96.0, 12f, now + 1, now))
        assertFalse(FixQuality.usable(Double.NaN, 96.0, 12f, now, now))
        assertFalse(FixQuality.usable(91.0, 96.0, 12f, now, now))
        assertFalse(FixQuality.usable(21.0, 181.0, 12f, now, now))
        assertFalse(FixQuality.usable(21.0, 96.0, 0f, now, now))
        assertFalse(FixQuality.usable(21.0, 96.0, Float.NaN, now, now))
        assertTrue(FixQuality.usableWallTime(21.0, 96.0, 12f, 1_000_000, 1_119_000))
        assertFalse(FixQuality.usableWallTime(21.0, 96.0, 12f, 1_000_000, 1_121_000))
    }
    @Test fun rasterHeaderAndBitOrderMatchEscPos() {
        val bytes = EscPos.raster(16, 2) { x, y -> (y == 0 && x in listOf(0, 7, 8)) || (y == 1 && x == 15) }
        assertArrayEquals(byteArrayOf(0x1d, 0x76, 0x30, 0, 2, 0, 2, 0, 0x81.toByte(), 0x80.toByte(), 0, 1), bytes)
        assertEquals(8 + 72 * 128, EscPos.raster(576, 128) { _, _ -> false }.size)
    }
    @Test fun physicalWidthsAndDefaultsAreExplicit() {
        assertEquals(142, EscPos.pagePoints(50)); assertEquals(164, EscPos.pagePoints(58)); assertEquals(227, EscPos.pagePoints(80))
        assertEquals(384, EscPos.defaultDots(58)); assertEquals(576, EscPos.defaultDots(80))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidRasterWidthIsRejected() { EscPos.raster(385, 1) { _, _ -> false } }
}

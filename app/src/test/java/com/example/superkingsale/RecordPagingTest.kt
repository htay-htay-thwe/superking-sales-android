package com.example.superkingsale

import com.example.superkingsale.data.Record
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordPagingTest {
    @Test fun appendedPages_useABoundedDeduplicatedWindow() {
        fun page(from: Int, to: Int, current: Int) = Record.parse(
            """{"data":[${(from..to).joinToString { "{\"id\":$it}" }}],"meta":{"current_page":$current,"last_page":20}}"""
        )

        val first = page(1, 120, 1)
        val next = page(101, 220, 2)
        val merged = first.appendPage(next)

        assertEquals(200, merged.rows("data").size)
        assertEquals(21, merged.rows("data").first().id)
        assertEquals(220, merged.rows("data").last().id)
        assertEquals(2, merged.obj("meta").number("current_page"))
    }
}

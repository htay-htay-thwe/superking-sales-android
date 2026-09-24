package com.example.superkingsale

import com.example.superkingsale.sale.SaleMath
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class SaleMathTest {
    @Test fun discountsRoundHalfUpLikeServer() {
        assertEquals(99L, SaleMath.lineTotal(101, 1, BigDecimal("1.5"), 0))
        assertEquals(95L, SaleMath.lineTotal(101, 1, BigDecimal("0.5"), 5))
    }
    @Test fun largeMmkAmountsDoNotLoseFloatingPointPrecision() {
        assertEquals(999999999999998L, SaleMath.lineTotal(999999999999999, 1, BigDecimal.ZERO, 1))
    }
    @Test fun rejectsFractionalAndNegativeQuantities() {
        assertNull(SaleMath.whole("1.5", 1)); assertNull(SaleMath.whole("-1"))
        assertNull(SaleMath.whole("0", 1)); assertEquals(12L, SaleMath.whole("12", 1))
    }
    @Test fun validatesDiscountPrecisionAndRange() {
        assertNull(SaleMath.discount("100.01")); assertNull(SaleMath.discount("-1"))
        assertNull(SaleMath.discount("0.001")); assertNotNull(SaleMath.discount("12.50"))
    }
    @Test fun reductionsCanBeDetectedBeforePosting() {
        assertTrue(SaleMath.lineTotal(100, 1, BigDecimal("50"), 51) < 0)
    }
}


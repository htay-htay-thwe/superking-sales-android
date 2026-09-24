package com.example.superkingsale.sale

import java.math.BigDecimal
import java.math.RoundingMode

data class SaleLine(
    val productId: Long = 0, val unitId: Long = 0, val quantity: String = "1",
    val focUnitId: Long = 0, val focQuantity: String = "0",
    val discount: String = "0", val promotionTitle: String = "", val promotion: String = "0",
)
data class SaleDraft(
    val id: Long = 0, val reference: String = "", val step: Int = 1,
    val customerId: Long = 0, val paymentType: String = "cash", val paymentMethod: String = "cash",
    val notes: String = "", val cashback: String = "0", val invoicePromotion: Long = 0,
    val invoicePromotionTitle: String = "", val lines: List<SaleLine> = emptyList(),
    val latitude: Double? = null, val longitude: Double? = null, val accuracy: Float? = null,
    val capturedAt: Long = 0, val pendingCreateJson: String? = null,
)
object SaleMath {
    fun lineTotal(price: Long, quantity: Long, discount: BigDecimal, promotion: Long): Long {
        val gross = BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(quantity))
        val reduction = gross.multiply(discount).divide(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP)
        return gross.subtract(reduction).subtract(BigDecimal.valueOf(promotion)).longValueExact()
    }
    fun whole(value: String, min: Long = 0): Long? = value.toLongOrNull()?.takeIf { it >= min }
    fun discount(value: String): BigDecimal? = value.toBigDecimalOrNull()?.takeIf {
        it >= BigDecimal.ZERO && it <= BigDecimal(100) && it.scale() <= 2
    }
}


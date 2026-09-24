package com.example.superkingsale.printing

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.superkingsale.data.Record
import com.example.superkingsale.ui.money
import java.io.OutputStream

/** Android shapes Unicode (including Myanmar) before rasterizing; printer firmware needs no Myanmar font. */
class ReceiptDocument(sale: Record, branding: Record, val width: Int, private val translate: (String) -> String,
                      test: Boolean = false, large: Boolean = false) {
    private data class Block(val layout: StaticLayout, val top: Int)
    private val blocks = mutableListOf<Block>()
    private val margin = 12
    var height: Int = margin
        private set
    init {
        require(width in 200..832)
        val fontSize = if (large) 26f else 22f
        fun text(value: String, bold: Boolean = false, size: Float = fontSize) {
            if (value.isBlank()) return
            require(value.length <= 30000) { "Receipt text is too long." }
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = size; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT }
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width - margin * 2)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true).setLineSpacing(3f, 1f).build()
            blocks += Block(layout, height); height += layout.height + 8
            require(height <= 100000) { "Receipt is too long. Use Android Print for this invoice." }
        }
        fun t(value: String) = translate(value)
        if (test) {
            text(t("TEST PRINT — NOT A SALE"), true, 28f)
            text("Super King · $width dots")
            text("မြန်မာစာ စမ်းသပ်ပုံနှိပ်ခြင်း\nကုန်ပစ္စည်း၊ အရေအတွက်၊ လျှော့ဈေး၊ ကျသင့်ငွေ\nကျပ် ၁၂၃၄၅၆၇၈၉၀ / 1234567890")
            text("ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789 + − × %")
            text(t("Check Myanmar marks, clipping, darkness and paper feed. No business record is created."))
        } else {
            text(branding.text("business_name", "Super King"), true, 28f)
            text(branding.text("business_tagline"))
            text(branding.text("business_address")); text(branding.text("business_phone") + "\n" + branding.text("business_email"))
            text(t("INVOICE") + " · " + sale.text("reference"), true)
            if (sale.text("status") == "voided") text(t("VOID"), true, 32f)
            text(t(sale.text("status")) + " · " + sale.text("posted_at", sale.text("created_at")))
            text(t("Payment") + ": " + t(sale.text("payment_type")) + " · " + t(sale.text("payment_method_name")))
            text(t("Representative") + ": " + sale.obj("representative").name)
            text(t("Trip") + ": " + sale.obj("trip").text("reference"))
            text(t("Coverage") + ": " + sale.obj("region").name)
            val customer = sale.obj("customer")
            text(t("Bill to") + ": " + customer.name, true)
            text(listOf(customer.text("code"), customer.text("phone"), customer.text("address")).filter { it.isNotBlank() }.joinToString("\n"))
            for (line in sale.rows("items")) {
                text("────────────────────")
                text(line.obj("product").name, true)
                text(line.obj("product").text("sku"))
                text("${line.number("quantity")} ${line.obj("unit").name} × ${money(line.number("unit_price"))}")
                text(t("FOC") + ": ${line.number("foc_quantity")} ${line.obj("foc_unit").name}")
                text(t("Discount") + ": ${line.text("discount_percentage")}% (${money(line.number("discount_amount"))})")
                text(line.text("promotion_title", t("Promotion")) + ": " + money(line.number("promotion_amount")))
                text(t("Line total") + ": " + money(line.number("line_total")), true)
            }
            text("────────────────────")
            for ((label, key) in listOf("Gross" to "gross_amount", "Item discounts" to "total_discount", "Item promotions" to "total_item_promotion",
                "Invoice promotion" to "promotion_amount", "Cashback" to "cashback_amount")) text(t(label) + ": " + money(sale.number(key)))
            text(sale.text("promotion_title"))
            text(t("Payable total") + ": " + money(sale.number("total_amount")), true, 28f)
            text(sale.text("notes")); text(sale.text("void_reason"))
            text(t("Customer signature") + "\n____________________")
            text(t("Authorized signature") + "\n____________________")
            text(branding.text("invoice_footer", t("Thank you for your business")))
        }
        height += margin
    }
    fun draw(canvas: Canvas, from: Int, rows: Int) {
        canvas.drawColor(Color.WHITE)
        for (block in blocks) if (block.top < from + rows && block.top + block.layout.height > from) {
            canvas.save(); canvas.translate(margin.toFloat(), (block.top - from).toFloat()); block.layout.draw(canvas); canvas.restore()
        }
    }
    fun rasterChunks(): Sequence<ByteArray> = sequence {
        val bitmap = Bitmap.createBitmap(width, 128, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * 128)
        try {
            for (y in 0 until height step 128) {
                val rows = minOf(128, height - y)
                draw(Canvas(bitmap), y, rows)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, rows)
                yield(EscPos.raster(width, rows) { x, row ->
                    val pixel = pixels[row * width + x]
                    (Color.red(pixel) * 299 + Color.green(pixel) * 587 + Color.blue(pixel) * 114) / 1000 < 180
                })
            }
        } finally { bitmap.recycle() }
    }
    fun pdf(output: OutputStream, paperMm: Int) {
        require(paperMm in listOf(50, 58, 80))
        val pageWidth = EscPos.pagePoints(paperMm)
        val scale = pageWidth.toFloat() / width
        val maxRows = (EscPos.pagePoints(297) / scale).toInt()
        val pdf = PdfDocument()
        try {
            var from = 0; var pageNumber = 1
            while (from < height) {
                var to = minOf(height, from + maxRows)
                // Keep shaped text lines intact at page boundaries.
                if (to < height) for (block in blocks) {
                    if (block.top < to && block.top + block.layout.height > to) {
                        val line = block.layout.getLineForVertical(to - block.top)
                        to = block.top + block.layout.getLineTop(line)
                        break
                    }
                }
                check(to > from)
                val rows = to - from
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, kotlin.math.ceil(rows * scale).toInt().coerceAtLeast(1), pageNumber++).create())
                page.canvas.scale(scale, scale); page.canvas.clipRect(0, 0, width, rows); draw(page.canvas, from, rows)
                pdf.finishPage(page); from = to
            }
            pdf.writeTo(output)
        } finally { pdf.close() }
    }
}

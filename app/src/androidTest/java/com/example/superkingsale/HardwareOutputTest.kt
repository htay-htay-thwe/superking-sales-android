package com.example.superkingsale

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.superkingsale.data.Record
import com.example.superkingsale.printing.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class HardwareOutputTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestSalesApplication>()
    @Test fun myanmarPdfHasExactWidthsAndRasterInk() {
        for (paper in listOf(50, 58, 80)) {
            val receipt = ReceiptDocument(Record(), Record(), EscPos.defaultDots(paper), { it }, test = true)
            val file = File(app.filesDir, "receipt-test-$paper.pdf")
            file.outputStream().use { receipt.pdf(it, paper) }
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                assertTrue(renderer.pageCount >= 1)
                renderer.openPage(0).use { page ->
                    assertEquals(EscPos.pagePoints(paper), page.width)
                    val bitmap = Bitmap.createBitmap(page.width * 3, page.height * 3, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    File(app.filesDir, "receipt-test-$paper.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            val chunks = receipt.rasterChunks().toList()
            assertTrue(chunks.size > 1)
            assertTrue(chunks.any { it.drop(8).any { pixel -> pixel != 0.toByte() } })
            assertTrue(chunks.all { it.size <= 8 + EscPos.defaultDots(paper) / 8 * 128 })
        }
    }
    @Test fun longInvoicePaginatesWithoutChangingWidth() {
        val item = """{"product":{"name":"မြန်မာ ကုန်ပစ္စည်း စမ်းသပ်"},"quantity":1,"unit":{"name":"ဘူး"},"unit_price":12345,"line_total":12345}"""
        val sale = Record.parse("""{"reference":"TEST-LONG","items":[${List(30) { item }.joinToString(",")}]}""")
        val file = File(app.filesDir, "receipt-long.pdf")
        file.outputStream().use { ReceiptDocument(sale, Record(), 384, { it }).pdf(it, 58) }
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            assertTrue(renderer.pageCount > 1)
            repeat(renderer.pageCount) { index -> renderer.openPage(index).use { assertEquals(164, it.width); assertTrue(it.height in 1..842) } }
        }
    }
    @Test fun tcpSendsExactlyOnceToLocalTestReceiver() {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            try {
                val received = executor.submit<ByteArray> { server.accept().use { it.getInputStream().readBytes() } }
                val chunk = EscPos.raster(384, 2) { x, y -> x == y }
                PrinterTransport(app).use { it.send(PrinterTarget("tcp", "127.0.0.1", server.localPort), sequenceOf(chunk), false) {} }
                assertArrayEquals(EscPos.initialize + chunk + EscPos.feed, received.get(8, TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }
    @Test fun nativePrinterSettingsValidateAndSurviveRotation() {
        app.seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(1200)
            scenario.onActivity { it.open(R.id.profile) }
            Thread.sleep(800)
            onView(withText("Thermal printer / PDF")).perform(scrollTo(), click())
            onView(withHint("Connection")).perform(scrollTo(), click())
            onView(withText("Wi-Fi / TCP")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click())
            onView(withHint("Printable dots")).perform(scrollTo(), replaceText("385"), closeSoftKeyboard())
            scenario.recreate()
            onView(withHint("Printable dots")).check(matches(withText("385")))
            onView(withText("Print test receipt")).perform(scrollTo(), click())
            onView(withText("Printable dots must be a multiple of 8 from 200 to 832.")).perform(scrollTo()).check(matches(isDisplayed()))
            assertTrue(app.requests.none { it.method != "GET" })
        }
        app.getSharedPreferences("printer.7", 0).edit().clear().commit()
    }
    @Test fun myanmarPrinterAndFinancialLabelsRenderNatively() {
        app.seed()
        val previousLanguage = app.getSharedPreferences("appearance", 0).getString("language", "en")
        app.getSharedPreferences("appearance", 0).edit().putString("language", "my").commit()
        val translate = com.example.superkingsale.ui.Translations.translator(app)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                Thread.sleep(1200); scenario.onActivity { it.open(R.id.profile) }; Thread.sleep(800)
                onView(withText(translate.translate("Thermal printer / PDF"))).perform(scrollTo(), click())
                onView(withHint(translate.translate("Paper width"))).perform(scrollTo()).check(matches(withText("58 mm")))
                val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                File(app.filesDir, "myanmar-printer-settings.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                onView(withText(translate.translate("Close"))).perform(click())
                scenario.onActivity { it.open(R.id.customers) }; Thread.sleep(800)
                scenario.onActivity { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list).scrollToPosition(3) }
                onView(withText(translate.translate("Collect credit"))).perform(click())
                onView(withHint(translate.translate("Amount (MMK)"))).perform(replaceText("0"), closeSoftKeyboard())
                onView(withText(translate.translate("Record collection"))).perform(click())
                onView(withText(translate.translate("Check the amount and payment method."))).check(matches(isDisplayed()))
                assertTrue(app.requests.none { it.method != "GET" })
            }
        } finally { app.getSharedPreferences("appearance", 0).edit().putString("language", previousLanguage).commit() }
    }
}

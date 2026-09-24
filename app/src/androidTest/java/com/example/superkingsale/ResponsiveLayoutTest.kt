package com.example.superkingsale

import android.graphics.Bitmap
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.superkingsale.ui.ResponsiveGrid
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run at both 390dp and 1440dp window widths. All API calls stay inside MockWebServer. */
@RunWith(AndroidJUnit4::class)
class ResponsiveLayoutTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestSalesApplication>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun settle(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.currentTimeMillis() + 15000
        var ready = false
        do {
            Thread.sleep(150)
            scenario.onActivity { activity ->
                val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list)
                val form = activity.findViewById<android.widget.LinearLayout>(R.id.form)
                ready = !activity.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing &&
                    (if (list.isVisible) (list.adapter?.itemCount ?: 0) > 0 else form.childCount > 0)
            }
        } while (!ready && System.currentTimeMillis() < deadline)
        assertTrue("Screen did not finish rendering", ready)
        instrumentation.waitForIdleSync()
        Thread.sleep(700)
    }
    private fun screenshot(name: String) {
        val automation = instrumentation.uiAutomation
        val deadline = System.currentTimeMillis() + 5000
        var foreground: String?
        do {
            foreground = automation.rootInActiveWindow?.packageName?.toString()
            if (foreground == app.packageName) break
            Thread.sleep(100)
        } while (System.currentTimeMillis() < deadline)
        assertEquals("An OS dialog must not obscure visual acceptance", app.packageName, foreground)
        val variant = InstrumentationRegistry.getArguments().getString("screenshots", "phone")
        val folder = File(app.getExternalFilesDir(null), "redesign/$variant").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        checkNotNull(bitmap)
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun captureScreensAndVerifyResponsiveNavigation() {
        val arguments = InstrumentationRegistry.getArguments()
        val theme = arguments.getString("theme", "1").toInt()
        app.getSharedPreferences("appearance", 0).edit().putString("language", arguments.getString("language", "en"))
            .putFloat("fontScale", arguments.getString("fontScale", "1.0").toFloat()).putInt("theme", theme).apply()
        instrumentation.runOnMainSync { androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(theme) }
        app.seed()
        val originalDispatcher = app.server.dispatcher
        app.server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): okhttp3.mockwebserver.MockResponse {
                val response = originalDispatcher.dispatch(request)
                if (request.requestUrl!!.encodedPath.endsWith("/dashboard")) {
                    val sales = (1..5).joinToString(",") { TestSalesApplication.sale.replace("\"id\":44", "\"id\":${44 + it}").replace("SAL-044", "SAL-0000${44 + it}").replace("draft", if (it == 1) "voided" else "posted") }
                    val stock = """{"id":1,"product":${TestSalesApplication.product},"quantity":50,"foc_quantity":5,"pending_quantity":0}"""
                    response.setBody("""{"representative":{"id":7,"name":"Test Representative"},"kpis":{"today_sales":630346,"today_cash_sales":561913,"today_credit_sales":68433,"stock_units":1240,"stock_products":13,"cash_hold":12000,"pending_receivings":1},"recent_sales":[$sales],"stock":[$stock],"pending_receivings":[${TestSalesApplication.transfer}],"as_of":"Sep 18, 2026"}""")
                }
                return response
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            settle(scenario)
            val screens = listOf(R.id.home to "01-dashboard", R.id.trip to "02-trip", R.id.stock to "03-stock", R.id.customers to "04-customers",
                R.id.customer_new to "05-customer-new", R.id.sales to "06-sales", R.id.cash to "07-cash", R.id.sale_detail to "08-sale-detail",
                R.id.receiving to "09-receiving", R.id.new_sale to "10-sale-information", R.id.profile to "11-profile")
            screens.forEach { (id, name) ->
                scenario.onActivity { it.open(id, when (id) { R.id.sale_detail -> 44; R.id.receiving -> 9; else -> 0 }) }
                settle(scenario)
                scenario.onActivity {
                    val wide = it.resources.configuration.screenWidthDp >= 600
                    assertEquals(wide, it.findViewById<View>(R.id.wide_nav).isVisible)
                    assertEquals(!wide, it.findViewById<View>(R.id.bottom_nav).isVisible)
                    assertTrue(it.findViewById<View>(R.id.account_menu).width >= (48 * it.resources.displayMetrics.density).toInt())
                    val menu = it.findViewById<View>(R.id.account_menu)
                    val visible = android.graphics.Rect()
                    assertTrue("Account menu must remain visible", menu.getGlobalVisibleRect(visible))
                    assertEquals("Account menu must not be clipped", menu.width, visible.width())
                    assertEquals("Super King Test", it.findViewById<TextView>(R.id.business_name).text.toString())
                }
                screenshot(name)
            }
            assertTrue("Visual review must not change business records", app.requests.none { it.method != "GET" })
            scenario.onActivity { it.open(R.id.login) }
            settle(scenario); screenshot("12-login")
        }
        app.server.dispatcher = originalDispatcher
    }
    @Test fun gridReflowsWithoutReplacingFormFields() {
        instrumentation.runOnMainSync {
            val context = androidx.appcompat.view.ContextThemeWrapper(app, R.style.Theme_SuperKingSale)
            val grid = ResponsiveGrid(context)
            val first = android.widget.EditText(context).apply { setText("Retained draft") }
            grid.addView(first); grid.addView(android.widget.EditText(context))
            fun layout(widthDp: Int) {
                val width = (widthDp * context.resources.displayMetrics.density).toInt()
                grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                grid.layout(0, 0, width, grid.measuredHeight)
            }
            layout(360); assertTrue(grid.getChildAt(1).top > first.top)
            layout(900); assertEquals(first.top, grid.getChildAt(1).top)
            assertSame(first, grid.getChildAt(0)); assertEquals("Retained draft", first.text.toString())
        }
    }
    @Test fun workspaceTextColoursHaveReadableContrast() {
        for (night in listOf(android.content.res.Configuration.UI_MODE_NIGHT_NO, android.content.res.Configuration.UI_MODE_NIGHT_YES)) {
            val configuration = android.content.res.Configuration(app.resources.configuration).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or night
            }
            val context = app.createConfigurationContext(configuration)
            val surface = context.getColor(R.color.workspace_surface)
            for (foreground in listOf(R.color.workspace_primary, R.color.workspace_text, R.color.workspace_muted)) {
                assertTrue("Text contrast must reach 4.5:1", androidx.core.graphics.ColorUtils.calculateContrast(context.getColor(foreground), surface) >= 4.5)
            }
        }
    }
    @org.junit.After fun restoreAppearance() {
        app.getSharedPreferences("appearance", 0).edit().putString("language", "en").putFloat("fontScale", 1f).putInt("theme", 1).apply()
        instrumentation.runOnMainSync { androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(1) }
    }
}

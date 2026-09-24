package com.example.superkingsale

import android.graphics.*
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class BrandingTest {
    @Test fun adaptiveAndMonochromeAssetsRenderWithTransparentMargins() {
        val app = ApplicationProvider.getApplicationContext<TestSalesApplication>()
        val icon = app.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        assertNotNull(icon.monochrome)
        assertEquals(R.mipmap.ic_launcher, app.applicationInfo.icon)
        val source = BitmapFactory.decodeResource(app.resources, R.drawable.superking_eagle_mono)
        assertEquals(0, Color.alpha(source.getPixel(0, 0)))
        assertTrue(source.hasAlpha()); source.recycle()
        val preview = Bitmap.createBitmap(1000, 620, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(preview); canvas.drawColor(Color.rgb(232, 236, 240))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 22f }
        val items = listOf("Adaptive" to icon, "Round" to app.getDrawable(R.mipmap.ic_launcher_round)!!,
            "Themed" to AdaptiveIconDrawable(android.graphics.drawable.ColorDrawable(Color.rgb(255, 222, 112)), icon.monochrome!!.mutate().apply { setTint(Color.rgb(40, 31, 0)) }))
        items.forEachIndexed { index, (label, drawable) ->
            val x = 30 + index * 330
            drawable.setBounds(x, 30, x + 280, 310); drawable.draw(canvas)
            canvas.drawText(label, x.toFloat(), 350f, text)
        }
        for ((index, color) in listOf(Color.WHITE, Color.rgb(16, 24, 32)).withIndex()) {
            val x = index * 500
            val paint = Paint().apply { this.color = color }; canvas.drawRect(x.toFloat(), 380f, x + 500f, 620f, paint)
            paint.color = Color.WHITE; canvas.drawCircle(x + 250f, 500f, 96f, paint)
            app.getDrawable(R.mipmap.ic_launcher)!!.apply { setBounds(x + 154, 404, x + 346, 596); draw(canvas) }
        }
        File(app.filesDir, "branding-preview.png").outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
        preview.recycle()
    }
    @Test fun startingThemeHandsOffToMainActivityWithoutNetworkDelay() {
        val app = ApplicationProvider.getApplicationContext<TestSalesApplication>(); app.seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<android.view.View>(R.id.nav_host))
                val value = android.util.TypedValue()
                assertTrue(activity.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true))
            }
        }
    }
}

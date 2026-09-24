package com.example.superkingsale

import android.Manifest
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationUiTest {
    @Test fun deniedPermissionOffersRecoveryWithoutChangingDraft() {
        Assume.assumeTrue("Permission-denial test is emulator-only", android.os.Build.MODEL.startsWith("sdk_") || android.os.Build.FINGERPRINT.contains("generic"))
        val app = ApplicationProvider.getApplicationContext<TestSalesApplication>()
        Assume.assumeTrue("Permission-denial UX uses an ungranted test device", ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        app.seed()
        val draft = app.store.get("draft.7.sale.0")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(1200); scenario.onActivity { it.open(R.id.profile) }; Thread.sleep(800)
            onView(withText("Test device GPS")).perform(scrollTo(), click())
            onView(withText("Location access")).check(matches(isDisplayed()))
            onView(withText("Cancel")).perform(click())
            onView(withText("Test device GPS")).perform(click())
            onView(withText("Continue")).perform(click())
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            var deny: AccessibilityNodeInfo? = null
            val deadline = System.currentTimeMillis() + 10000
            fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                if (node == null) return null
                if (node.viewIdResourceName?.endsWith("permission_deny_button") == true || node.text?.toString() in listOf("Don't allow", "Don’t allow")) return node
                for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
                return null
            }
            while (deny == null && System.currentTimeMillis() < deadline) { deny = find(automation.rootInActiveWindow); if (deny == null) Thread.sleep(100) }
            Assert.assertNotNull("Missing OS permission denial control", deny)
            Assert.assertTrue(deny!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            Thread.sleep(500)
            onView(withText("Open settings")).check(matches(isDisplayed()))
            onView(withText("Cancel")).perform(click())
            Assert.assertEquals(draft, app.store.get("draft.7.sale.0"))
            Assert.assertTrue(app.requests.none { it.method != "GET" })
        }
    }
}

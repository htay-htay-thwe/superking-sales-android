package com.example.superkingsale

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matchers.*
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesFlowTest {
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val app get() = ApplicationProvider.getApplicationContext<TestSalesApplication>()
    @Before fun start() {
        app.seed()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText("Hello, Test Representative")
    }
    @After fun finish() { scenario.close() }
    private fun awaitText(text: String) {
        val deadline = System.currentTimeMillis() + 12000
        var failure: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try { onView(allOf(withText(text), not(isDescendantOfA(withId(R.id.wide_nav))))).check(matches(isDisplayed())); return }
            catch (e: Throwable) { failure = e; Thread.sleep(100) }
        }
        throw AssertionError("Missing view: $text", failure)
    }
    private fun open(id: Int, record: Long = 0) {
        scenario.onActivity { it.open(id, record) }
    }
    @Test fun mainTabsAndNativeNavigationLoad() {
        onView(withId(R.id.trip)).perform(click())
        awaitText("Yangon route")
        onView(withId(R.id.sales)).perform(click())
        awaitText("Sales activity")
        onView(withId(R.id.cash)).perform(click())
        awaitText("Current hold")
        open(R.id.customers)
        awaitText("New customer")
        open(R.id.stock)
        awaitText("My stock")
        Assert.assertTrue(app.requests.none { it.method != "GET" })
    }
    @Test fun saleWizardPreservesQuantityAcrossRecreationAndSavesDraft() {
        onView(withId(R.id.new_sale)).perform(click())
        awaitText("Sale information")
        onView(withText("Continue")).perform(scrollTo(), click())
        awaitText("Step 2 of 4 · Products")
        onView(withText("Continue")).perform(click())
        awaitText("Quantities & offers")
        onView(withHint("Quantity")).perform(scrollTo(), replaceText("2"), closeSoftKeyboard())
        scenario.recreate()
        onView(withHint("Quantity")).check(matches(withText("2")))
        onView(withText("Continue")).perform(scrollTo(), click())
        awaitText("Review & submit")
        onView(withText("Save draft")).perform(scrollTo(), click())
        awaitText("SAL-044 saved as draft.")
        Assert.assertEquals(1, app.requests.count { it.method == "PUT" && it.path == "/public/api/sales/sales/44" })
    }
    @Test fun insufficientStockStaysOnQuantityStep() {
        onView(withId(R.id.new_sale)).perform(click()); awaitText("Sale information")
        onView(withText("Continue")).perform(scrollTo(), click())
        onView(withText("Continue")).perform(click())
        awaitText("Quantities & offers")
        onView(withHint("Quantity")).perform(scrollTo(), replaceText("999"), closeSoftKeyboard())
        onView(withText("Continue")).perform(scrollTo(), click())
        awaitText("Test Product: quantity exceeds available paid stock.")
        Assert.assertTrue(app.requests.none { it.method != "GET" })
    }
    @Test fun receivingRequiresExplicitConfirmation() {
        open(R.id.receiving, 9)
        awaitText("REP-009")
        // Scroll the recycler to the confirmation card without touching server state.
        scenario.onActivity { activity ->
            activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list).scrollToPosition(3)
        }
        awaitText("Receive all stock")
        onView(withText("Receive all stock")).perform(click())
        awaitText("Receive all stock?")
        onView(withText("Cancel")).perform(click())
        Assert.assertTrue(app.requests.none { it.method != "GET" })
    }
    @Test fun expiredSessionReturnsToSignIn() {
        app.unauthorized = true
        onView(withId(R.id.swipe_refresh)).perform(swipeDown())
        awaitText("Super King")
        onView(allOf(withText("Sign in"), isAssignableFrom(android.widget.Button::class.java))).check(matches(isDisplayed()))
        Assert.assertNull(app.repository.user.value)
        Assert.assertNull(app.store.get("draft.7.sale.0"))
    }
    @Test fun creditPresetsRetainAmountAcrossRotationWithoutSubmitting() {
        open(R.id.customers)
        scenario.onActivity { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list).scrollToPosition(3) }
        awaitText("Collect credit")
        onView(withText("Collect credit")).perform(click())
        awaitText("Record collection")
        onView(withText("25%")).perform(click())
        onView(withHint("Amount (MMK)")).check(matches(withText("1250")))
        onView(withText("50%")).perform(click())
        scenario.recreate()
        onView(withHint("Amount (MMK)")).check(matches(withText("2500")))
        onView(withText("Full")).perform(click())
        onView(withHint("Amount (MMK)")).check(matches(withText("5000")))
        onView(withText("Cancel")).perform(click())
        Assert.assertTrue(app.requests.none { it.method != "GET" })
    }
    @Test fun translationTemplatesWorkOnAndroidRuntime() {
        // Android ICU regex parsing is stricter than the desktop JVM for literal braces.
        val translator = com.example.superkingsale.ui.TextTranslator(mapOf("Hello, {name}" to "မင်္ဂလာပါ {name}"))
        Assert.assertEquals("မင်္ဂလာပါ Way8", translator.translate("Hello, Way8"))
    }
    @Test fun offlineSaveKeepsDraftAndSendsNoMutation() {
        app.networkAvailable = false
        try {
            scenario.recreate(); awaitText("Hello, Test Representative")
            onView(withId(R.id.connection)).check(matches(isDisplayed()))
            open(R.id.new_sale); awaitText("Sale information")
            onView(withText("Continue")).perform(scrollTo(), click())
            onView(withText("Continue")).perform(click())
            awaitText("Quantities & offers")
            onView(withText("Continue")).perform(scrollTo(), click())
            awaitText("Review & submit")
            val draft = app.store.get("draft.7.sale.0")
            onView(withText("Save draft")).perform(scrollTo(), click())
            awaitText("Internet connection is required to complete this transaction.")
            Assert.assertEquals(draft, app.store.get("draft.7.sale.0"))
            Assert.assertTrue(app.requests.none { it.method != "GET" })
        } finally { app.networkAvailable = true }
    }
}

package com.example.superkingsale

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PullRefreshTest {
    private val app get() = ApplicationProvider.getApplicationContext<TestSalesApplication>()
    private fun idle(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.currentTimeMillis() + 15000
        do {
            Thread.sleep(150)
            var ready = false
            scenario.onActivity { ready = !it.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).isRefreshing }
            if (ready) return
        } while (System.currentTimeMillis() < deadline)
        fail("Refresh did not finish")
    }
    private fun pull(scenario: ActivityScenario<MainActivity>, path: String) {
        idle(scenario)
        val before = app.requests.count { it.requestUrl!!.encodedPath == "/public/api/sales/$path" }
        onView(withId(R.id.swipe_refresh)).perform(swipeDown())
        val deadline = System.currentTimeMillis() + 10000
        while (app.requests.count { it.requestUrl!!.encodedPath == "/public/api/sales/$path" } == before && System.currentTimeMillis() < deadline) Thread.sleep(100)
        idle(scenario)
        assertEquals(before + 1, app.requests.count { it.requestUrl!!.encodedPath == "/public/api/sales/$path" })
        onView(withText("Refresh")).check(doesNotExist())
    }
    @Test fun allWorkspaceDestinationsRefreshWithoutMutations() {
        app.seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(1200)
            val destinations = listOf(R.id.home to "dashboard", R.id.trip to "current-trip", R.id.sales to "sales",
                R.id.cash to "cash-hold", R.id.stock to "stock", R.id.customers to "customers",
                R.id.receiving to "receivings/9", R.id.sale_detail to "sales/44")
            for ((id, path) in destinations) {
                scenario.onActivity { it.open(id, if (id == R.id.receiving) 9 else if (id == R.id.sale_detail) 44 else 0) }
                Thread.sleep(400)
                pull(scenario, path)
            }
            assertTrue(app.requests.none { it.method != "GET" })
        }
    }
    @Test fun customerAndProfileInputSurvivePull() {
        app.seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(1200)
            for ((id, hint, path) in listOf(Triple(R.id.customer_new, "Customer name", "customer-options"), Triple(R.id.profile, "Name", "profile"))) {
                scenario.onActivity { it.open(id) }; Thread.sleep(400); idle(scenario)
                onView(withHint(hint)).perform(scrollTo(), replaceText("Unsaved edit"), closeSoftKeyboard())
                scenario.onActivity { it.findViewById<androidx.core.widget.NestedScrollView>(R.id.form_scroll).scrollTo(0, 0) }
                pull(scenario, path)
                onView(withHint(hint)).check(matches(withText("Unsaved edit")))
            }
            assertTrue(app.requests.none { it.method != "GET" })
        }
    }
    @Test fun saleDraftSurvivesPullAndRotation() {
        app.seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(1200); scenario.onActivity { it.open(R.id.new_sale) }; Thread.sleep(400); idle(scenario)
            onView(withHint("Notes")).perform(scrollTo(), replaceText("Keep my draft"), closeSoftKeyboard())
            scenario.onActivity { it.findViewById<androidx.core.widget.NestedScrollView>(R.id.form_scroll).scrollTo(0, 0) }
            pull(scenario, "sale-options")
            scenario.recreate(); idle(scenario)
            onView(withHint("Notes")).check(matches(withText("Keep my draft")))
            assertTrue(app.requests.none { it.method != "GET" })
        }
    }
    @Test fun failedRefreshStopsSpinnerKeepsContentAndCanRetry() {
        app.seed()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(1200); idle(scenario)
            val original = app.server.dispatcher
            app.server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): okhttp3.mockwebserver.MockResponse {
                    val response = original.dispatch(request)
                    return if (request.requestUrl!!.encodedPath.endsWith("/dashboard"))
                        response.setResponseCode(503).setBody("""{"message":"Test refresh unavailable"}""") else response
                }
            }
            try {
                pull(scenario, "dashboard")
                onView(withText("Route overview")).check(matches(isDisplayed()))
                onView(withId(R.id.status)).check(matches(isDisplayed()))
            } finally { app.server.dispatcher = original }
            pull(scenario, "dashboard")
            onView(withId(R.id.status)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            scenario.onActivity {
                val list = it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list)
                list.scrollBy(0, 700)
                assertTrue(it.findViewById<SwipeRefreshLayout>(R.id.swipe_refresh).canChildScrollUp())
            }
            assertTrue(app.requests.none { it.method != "GET" })
        }
    }
}

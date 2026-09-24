package com.example.superkingsale

import android.graphics.Bitmap
import android.os.Bundle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.example.superkingsale.data.*
import com.example.superkingsale.sale.SaleMath
import com.example.superkingsale.ui.tr
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.*
import org.junit.*
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.UUID

/** Never runs live from the default test command. Requires a separate runner AND explicit write opt-in.
 * Credentials exist only in instrumentation arguments. The journal contains test IDs, never credentials.
 * The emulator supplies simulated GPS; every created business document is labelled as QA.
 */
@RunWith(AndroidJUnit4::class)
class LiveBusinessProcessTest {
    private val args get() = InstrumentationRegistry.getArguments()
    private val app get() = ApplicationProvider.getApplicationContext<SalesApplication>()
    private val repo get() = app.repository
    private val journal by lazy { app.getSharedPreferences("live_acceptance", 0) }
    private lateinit var admin: Transport
    private var scenario: ActivityScenario<MainActivity>? = null
    private val tag get() = journal.getString("tag", "")!!
    private fun id(key: String) = journal.getLong(key, 0)
    private fun remember(key: String, value: Long): Long { check(journal.edit().putLong(key, value).commit()); return value }
    private fun note(value: String) {
        val previous = journal.getString("events", "").orEmpty()
        journal.edit().putString("events", previous + value + "\n").commit()
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", "LIVE: $value\n") })
    }
    private fun <T> io(block: suspend () -> T): T = runBlocking { block() }
    private fun get(path: String, query: Map<String, String> = emptyMap()) = io { repo.get(path, query) }
    private fun command(method: String, path: String, body: Map<String, Any?> = emptyMap(), safe: Boolean = true) =
        io { repo.command(method, path, body, safe) }
    private fun adminGet(path: String) = io { admin.read("api/admin/$path") }
    private fun adminWrite(path: String, body: Map<String, Any?> = emptyMap(), method: String = "POST") =
        io { admin.send(method, "api/admin/$path", body, UUID.randomUUID().toString()) }
    private fun once(key: String, create: () -> Record): Long = id(key).takeIf { it > 0 } ?: create().id.let {
        require(it > 0) { "Missing id for $key" }; remember(key, it); note("$key id=$it"); it
    }
    private fun expectFailure(status: Int? = null, block: () -> Unit): ApiFailure {
        try { block(); throw AssertionError("Expected server rejection") }
        catch (e: ApiFailure) { if (status != null) Assert.assertEquals(e.message, status, e.status); note("Expected rejection: ${e.status} ${e.code}"); return e }
    }
    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 55000
        while (System.currentTimeMillis() < until) { if (condition()) return; Thread.sleep(200) }
        throw AssertionError(message)
    }
    private fun awaitText(text: String) = awaitCondition("Missing native view: $text") {
        runCatching { onView(allOf(withText(text), not(isDescendantOfA(withId(R.id.wide_nav))))).check(matches(isDisplayed())) }.isSuccess
    }
    private fun open(destination: Int, record: Long = 0) { scenario!!.onActivity { it.open(destination, record) } }
    private fun scrollListEnd() { scenario!!.onActivity {
        val list = it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list)
        list.scrollToPosition((list.adapter!!.itemCount - 1).coerceAtLeast(0))
    } }
    private fun tap(text: String, scroll: Boolean = false) {
        val button = onView(allOf(withText(text), isAssignableFrom(android.widget.Button::class.java)))
        if (scroll) button.perform(scrollTo(), click()) else button.perform(click())
    }
    private fun field(hint: String, value: String) = onView(withHint(hint)).perform(scrollTo(), replaceText(value), closeSoftKeyboard())
    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        app.openFileOutput("live-$name.png", 0).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @After fun finish() { scenario?.close() }

    @Test fun approvedLiveBusinessProcess() {
        Assume.assumeTrue("Live writes are opt-in", args.getString("allowLiveWrites") == "YES")
        check(app.javaClass == SalesApplication::class.java) { "Use the standard AndroidJUnitRunner for live tests" }
        if (tag.isBlank()) journal.edit().putString("tag", "ANDROID-QA-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())).commit()
        admin = Transport("https://www.superkingmyanmar.com/public/", SessionCookies({ null }, {}))
        io {
            admin.csrf()
            admin.send("POST", "api/auth/login", mapOf("login" to args.getString("adminLogin"), "password" to args.getString("adminPassword"), "portal" to "admin"))
        }
        val phase = args.getString("phase", "probe")
        if (phase == "probe") {
            scenario = ActivityScenario.launch(MainActivity::class.java)
            awaitText("Super King")
            field("Username or email", args.getString("salesLogin")!!)
            field("Password", args.getString("salesPassword")!!)
            tap("Sign in", true)
            awaitCondition("Native login failed") { repo.user.value != null }
            val profile = get("profile").obj("representative")
            remember("representative", profile.id); remember("warehouse", profile.obj("primary_warehouse").id)
            remember("region", profile.rows("regions").first().id)
            awaitText("Hello, ${profile.name}")
            screenshot("dashboard")
            for ((destination, expected) in listOf(R.id.trip to "No active trip", R.id.cash to "Current hold",
                R.id.sales to "Sales activity", R.id.stock to "My stock", R.id.customers to "New customer", R.id.profile to "Profile & security")) {
                open(destination); awaitText(expected)
            }
            note("Native login and all read-only destinations passed; representative=${profile.id}")
            Assert.assertTrue(get("current-trip").obj("data").empty)
            Assert.assertEquals(0L, get("cash-hold").number("cash_hold"))
            Assert.assertEquals(0L, get("stock").obj("summary").number("on_hand"))
            return
        }
        io { repo.login(args.getString("salesLogin")!!, args.getString("salesPassword")!!) }
        when (phase) {
            "setup" -> setup()
            "native" -> nativeSale()
            "transactions" -> transactions()
            "ux" -> nativeFinancialForms()
            "profile" -> profileAndPrinting()
            "draftSeed" -> draftRecovery(false)
            "draftRestore" -> draftRecovery(true)
            "appearance", "printing" -> appearanceAndPrintSizes()
            "settle" -> settle()
            else -> error("Unknown phase")
        }
    }

    private fun setup() {
        if (id("trip") == 0L) Assert.assertTrue("Do not replace another trip", get("current-trip").obj("data").empty)
        val products = adminGet("products").rows("data")
        val product = products.first { p -> p.rows("units").any { u -> u.flag("is_base") && u.rows("prices").any { it.number("region_id") == id("region") && it.number("price") > 1000 } } }
        val base = product.rows("units").first { it.flag("is_base") }
        val selling = product.rows("units").first { it.flag("is_default_selling") }
        remember("product", product.id); remember("baseUnit", base.id); remember("sellingUnit", selling.id)
        remember("basePrice", base.rows("prices").first { it.number("region_id") == id("region") }.number("price"))
        remember("sellingPrice", selling.rows("prices").first { it.number("region_id") == id("region") }.number("price"))
        remember("factor", selling.number("conversion_factor"))
        val paid = maxOf(100, id("factor") * 3); remember("issuedPaid", paid); remember("issuedFoc", 10)
        val import = once("import") { adminWrite("stock-imports", mapOf("warehouse_id" to id("warehouse"), "notes" to "$tag TEST ONLY stock import",
            "items" to listOf(mapOf("product_id" to product.id, "product_unit_id" to base.id, "quantity" to paid + 10)))).obj("data") }
        if (adminGet("stock-imports/$import").obj("data").text("status") == "draft") adminWrite("stock-imports/$import/post")
        val trip = once("trip") {
            val vehicle = adminGet("trip-options").rows("vehicles").first { it.number("sales_representative_id") == id("representative") }
            adminWrite("trips", mapOf("title" to "$tag TEST ONLY route", "warehouse_id" to id("warehouse"), "region_id" to id("region"),
                "sales_representative_id" to id("representative"), "vehicle_id" to vehicle.id, "notes" to "$tag Android full lifecycle acceptance")).obj("data")
        }
        expectFailure(409) { get("sale-options") }
        val issue = once("issue") { adminWrite("representative-transfers", mapOf("trip_id" to trip, "source_warehouse_id" to id("warehouse"),
            "sales_representative_id" to id("representative"), "notes" to "$tag TEST ONLY issue",
            "items" to listOf(mapOf("product_id" to product.id, "product_unit_id" to base.id, "quantity" to paid, "foc_product_unit_id" to base.id, "foc_quantity" to 10)))).obj("data") }
        if (adminGet("representative-transfers/$issue").obj("data").text("status") == "draft") adminWrite("representative-transfers/$issue/dispatch")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText("Hello, ${get("profile").obj("representative").name}")
        if (get("receivings/$issue").obj("data").text("status") == "dispatched") {
            open(R.id.receiving, issue)
            awaitText(get("receivings/$issue").obj("data").text("reference"))
            scrollListEnd(); awaitText("Receive all stock"); tap("Receive all stock")
            awaitText("Receive all stock?"); tap("Confirm")
            awaitText("Hello, ${get("profile").obj("representative").name}")
        }
        Assert.assertEquals("received", get("receivings/$issue").obj("data").text("status"))
        Assert.assertEquals(paid, get("stock").obj("summary").number("on_hand"))
        Assert.assertEquals(10L, get("stock").obj("summary").number("foc_on_hand"))
        if (get("current-trip").obj("data").text("status") == "planning") adminWrite("trips/$trip/start")
        Assert.assertEquals("operation", get("current-trip").obj("data").text("status"))
        Assert.assertTrue(get("sale-options").rows("products").any { it.id == product.id })
        note("Native receiving verified paid=$paid FOC=10; trip=$trip operating")
    }

    private fun nativeSale() {
        if (id("nativeSale") == 0L) {
            val draft = io { repo.draft("sale.0") }?.let { Gson().fromJson(it, com.example.superkingsale.sale.SaleDraft::class.java) }
            check(draft?.pendingCreateJson == null && (draft?.id ?: 0L) == 0L) { "Reconcile the interrupted save before restarting native test" }
            if (draft != null) { check(draft.notes.startsWith(tag)); io { repo.draft(null, "sale.0") } }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText("Hello, ${get("profile").obj("representative").name}")
        if (id("customer") == 0L) {
            open(R.id.new_sale); awaitText("Sale information"); tap("Add new customer", true); awaitText("Create customer")
            field("Customer name", "$tag TEST SHOP")
            field("Address", "Android emulator QA fixture - not a real delivery address")
            field("Notes", "$tag test-only customer")
            tap("Create customer", true); awaitText("Sale information")
            val customer = get("customers", mapOf("search" to tag)).rows("data").single()
            remember("customer", customer.id)
            Assert.assertFalse(customer.flag("credit_allowed"))
        } else {
            open(R.id.new_sale); awaitText("Sale information")
            if (id("nativeSale") == 0L) {
                tap("Choose customer", true)
                onView(withHint("Search customers")).perform(replaceText(tag), closeSoftKeyboard())
                awaitText("$tag TEST SHOP"); tap("Select")
            }
        }
        if (id("nativeSale") > 0) { note("Native sale already recorded; no duplicate created"); return }
        // Inline customer creation should return to the wizard with the new customer selected.
        tap("Capture current location", true)
        awaitCondition("Emulator GPS did not reach the native location listener") {
            runCatching { onView(withText(startsWith("Location ready"))).check(matches(isDisplayed())) }.isSuccess
        }
        field("Notes", "$tag native cash sale; simulated emulator GPS, not field delivery")
        tap("Continue", true); awaitText("Step 2 of 4 · Products")
        tap("Add product"); tap("Continue"); awaitText("Quantities & offers")
        field("Quantity", "1"); field("Discount (%)", "1.5")
        field("Promotion title", "$tag item offer"); field("Promotion amount (MMK)", "100")
        scenario!!.recreate()
        onView(withHint("Quantity")).check(matches(withText("1")))
        tap("Continue", true); awaitText("Review & submit"); field("Cashback (MMK)", "50")
        screenshot("sale-review")
        tap("Save & review server total", true)
        awaitText("Post sale")
        val sale = get("sales", mapOf("search" to tag)).rows("data").first { it.text("status") == "draft" }
        remember("nativeSale", sale.id)
        val expected = SaleMath.lineTotal(id("sellingPrice"), 1, BigDecimal("1.5"), 100) - 50
        Assert.assertEquals(expected, sale.number("total_amount")); remember("nativeTotal", expected)
        tap("Post sale")
        awaitCondition("Native sale was not posted") { get("sales/${sale.id}").obj("data").text("status") == "posted" }
        Assert.assertEquals(expected, get("cash-hold").number("cash_hold"))
        Assert.assertEquals(id("issuedPaid") - id("factor"), get("stock").obj("summary").number("on_hand"))
        screenshot("posted-sale")
        note("Native four-step cash sale ${sale.id} posted; discount/promotion/cashback total=$expected; rotation and GPS verified")
    }

    private fun saleBody(type: String = "cash", method: String = "cash", quantity: Long = 1, foc: Long = 0): Map<String, Any?> = linkedMapOf(
        "customer_id" to id("customer"), "payment_type" to type, "payment_method" to method,
        "notes" to "$tag TEST ONLY Android transport; simulated QA coordinates", "cashback_amount" to 0,
        "creation_latitude" to 21.9588, "creation_longitude" to 96.0891, "location_accuracy_meters" to 10,
        "items" to listOf(mapOf("product_id" to id("product"), "product_unit_id" to id("baseUnit"), "quantity" to quantity,
            "foc_product_unit_id" to id("baseUnit"), "foc_quantity" to foc, "discount_percentage" to 0, "promotion_amount" to 0)))

    private fun transactions() {
        if (id("transactionsDone") > 0) return
        val methods = get("sale-options").rows("payment_methods")
        val cash = methods.first { it.flag("adds_to_cash_hold") }.text("key")
        val bank = methods.first { !it.flag("adds_to_cash_hold") }.text("key")
        val native = get("sales/${id("nativeSale")}").obj("data")
        Assert.assertEquals("posted", native.text("status"))
        expectFailure(409) { command("DELETE", "sales/${native.id}") }
        val draft = once("deletedDraft") { command("POST", "sales", saleBody(method = cash)).obj("data") }
        if (id("draftDeleted") == 0L) {
            val update = saleBody(method = cash, quantity = 2).filterKeys { it !in listOf("creation_latitude", "creation_longitude", "location_accuracy_meters") }
            val edited = command("PUT", "sales/$draft", update).obj("data")
            Assert.assertEquals(id("basePrice") * 2, edited.number("total_amount"))
            expectFailure(409) { command("POST", "trips/${id("trip")}/begin-ending", safe = false) }
            command("DELETE", "sales/$draft"); remember("draftDeleted", 1)
            expectFailure(404) { get("sales/$draft") }
        }
        // Laravel allows saving drafts; stock/credit enforcement is authoritative at posting.
        // The native wizard additionally validates these before saving for immediate field feedback.
        for ((key, body) in listOf("paidRejected" to saleBody(quantity = id("issuedPaid") + 1),
            "focRejected" to saleBody(foc = 11), "creditRejected" to saleBody(type = "credit"))) {
            if (id("$key.deleted") == 0L) {
                val rejected = once(key) { command("POST", "sales", body).obj("data") }
                expectFailure(409) { command("POST", "sales/$rejected/post") }
                command("DELETE", "sales/$rejected"); remember("$key.deleted", 1)
            }
        }
        adminWrite("customers/${id("customer")}/credit", mapOf("credit_allowed" to true, "credit_limit" to 10000000), "PUT")
        val credit = once("creditSale") { command("POST", "sales", saleBody("credit", cash, 2, 1)).obj("data") }
        if (get("sales/$credit").obj("data").text("status") == "draft") command("POST", "sales/$credit/post")
        val banking = once("bankSale") { command("POST", "sales", saleBody(method = bank)).obj("data") }
        if (get("sales/$banking").obj("data").text("status") == "draft") command("POST", "sales/$banking/post")
        Assert.assertEquals(id("nativeTotal"), get("cash-hold").number("cash_hold"))
        val price = id("basePrice")
        once("cashCollection") { command("POST", "credit-collections", mapOf("customer_id" to id("customer"), "amount" to price,
            "payment_method" to cash, "notes" to "$tag cash collection")).obj("data") }
        once("bankCollection") { command("POST", "credit-collections", mapOf("customer_id" to id("customer"), "amount" to price,
            "payment_method" to bank, "notes" to "$tag bank collection")).obj("data") }
        val customer = get("customers", mapOf("search" to tag)).rows("data").single()
        Assert.assertEquals(0L, customer.number("outstanding_amount"))
        Assert.assertEquals(id("nativeTotal") + price, get("cash-hold").number("cash_hold"))
        if (id("expenseDone") == 0L) {
            command("POST", "trips/${id("trip")}/expenses", mapOf("description" to "$tag test fuel", "amount" to 123, "notes" to "$tag not an actual payment"), false)
            remember("expenseDone", 1)
        }
        Assert.assertEquals(id("nativeTotal") + price, get("cash-hold").number("cash_hold"))
        val cancelled = once("cancelledCash") { command("POST", "cash-submissions", mapOf("amount" to 100, "notes" to "$tag cancel test")).obj("data") }
        if (id("cashCancelled") == 0L) {
            Assert.assertEquals(id("nativeTotal") + price - 100, get("cash-hold").number("available_to_submit"))
            command("POST", "cash-submissions/$cancelled/cancel", mapOf("reason" to "$tag cancellation acceptance")); remember("cashCancelled", 1)
        }
        val remaining = get("stock").obj("summary")
        Assert.assertEquals(id("issuedPaid") - id("factor") - 3, remaining.number("on_hand"))
        Assert.assertEquals(9L, remaining.number("foc_on_hand"))
        Assert.assertTrue(get("sales", mapOf("trip_id" to id("trip").toString(), "status" to "posted")).rows("data").size >= 3)
        Assert.assertTrue(get("sale-history-options", mapOf("period" to "today")).rows("trips").any { it.id == id("trip") })
        Assert.assertTrue(get("cash-transactions").rows("data").isNotEmpty())
        remember("transactionsDone", 1)
        note("CRUD, credit and banking sales, FOC, cash/bank collections, expense invariance, cash cancellation and history passed")
    }

    private fun settle() {
        if (id("completed") > 0) return
        val trip = id("trip")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText("Hello, ${get("profile").obj("representative").name}")
        if (get("current-trip").obj("data").text("status") == "operation") {
            val methods = get("sale-options").rows("payment_methods").size
            open(R.id.trip); awaitText("$tag TEST ONLY route")
            scenario!!.onActivity { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list).scrollToPosition(2 + methods) }
            awaitText("More actions"); tap("More actions"); onView(withText("Begin trip ending")).perform(click())
            awaitText("Begin trip ending?"); tap("Confirm")
            awaitCondition("Native trip ending failed") { get("current-trip").obj("data").text("status") == "ending" }
        }
        expectFailure(409) { get("sale-options") }
        expectFailure(409) { command("POST", "credit-collections", mapOf("customer_id" to id("customer"), "amount" to 1, "payment_method" to "cash")) }
        expectFailure(409) { adminWrite("trips/$trip/complete") }
        val stock = get("stock").obj("summary")
        val returned = once("return") { adminWrite("representative-returns", mapOf("trip_id" to trip, "target_warehouse_id" to id("warehouse"),
            "sales_representative_id" to id("representative"), "notes" to "$tag complete stock return",
            "items" to listOf(mapOf("product_id" to id("product"), "product_unit_id" to id("baseUnit"), "quantity" to stock.number("on_hand"),
                "foc_product_unit_id" to id("baseUnit"), "foc_quantity" to stock.number("foc_on_hand"))))).obj("data") }
        if (adminGet("representative-returns/$returned").obj("data").text("status") == "draft") adminWrite("representative-returns/$returned/post")
        val held = get("cash-hold").number("cash_hold")
        val submission = once("finalCash") {
            open(R.id.cash); awaitText("Return cash"); tap("Return cash"); awaitText("Return trip cash")
            onView(withHint("Amount (MMK)")).check(matches(withText(held.toString())))
            onView(withHint("Handover note")).perform(replaceText("$tag final office handover"), closeSoftKeyboard())
            tap("Submit for confirmation")
            awaitCondition("Native final return missing") { get("cash-submissions").rows("data").any { it.text("notes") == "$tag final office handover" } }
            get("cash-submissions").rows("data").first { it.text("notes") == "$tag final office handover" }
        }
        if (id("cashConfirmed") == 0L) {
            Assert.assertEquals(held, get("cash-hold").number("cash_hold"))
            Assert.assertEquals(0L, get("cash-hold").number("available_to_submit"))
            expectFailure(409) { adminWrite("trips/$trip/complete") }
            adminWrite("cash-submissions/$submission/confirm"); remember("cashConfirmed", 1)
        }
        Assert.assertEquals(0L, get("cash-hold").number("cash_hold"))
        Assert.assertEquals(0L, get("stock").obj("summary").number("on_hand"))
        Assert.assertEquals(0L, get("stock").obj("summary").number("foc_on_hand"))
        val completed = adminWrite("trips/$trip/complete", mapOf("notes" to "$tag Android acceptance complete, zero stock/cash variance")).obj("data")
        Assert.assertEquals("completed", completed.text("status"))
        Assert.assertEquals(0L, completed.number("stock_variance_units")); Assert.assertEquals(0L, completed.number("cash_variance_amount"))
        Assert.assertTrue(get("current-trip").obj("data").empty)
        remember("completed", trip)
        open(R.id.trip); awaitText("No active trip"); screenshot("completed-trip")
        open(R.id.cash); awaitText("Current hold"); screenshot("settled-cash")
        note("Trip $trip completed; paid/FOC stock=0, cash=0, customer outstanding=0, stock/cash variance=0")
    }

    private fun nativeFinancialForms() {
        if (id("uxDone") > 0) return
        val methods = get("sale-options").rows("payment_methods")
        val cash = methods.first { it.flag("adds_to_cash_hold") }.text("key")
        val credit = once("uxCredit") { command("POST", "sales", saleBody("credit", cash)).obj("data") }
        if (get("sales/$credit").obj("data").text("status") == "draft") command("POST", "sales/$credit/post")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText("Hello, ${get("profile").obj("representative").name}")
        if (id("uxCollection") == 0L) {
            open(R.id.customers); awaitText("New customer"); tap("Search")
            onView(withHint("Search")).perform(replaceText(tag), closeSoftKeyboard()); tap("Apply")
            awaitText("Collect credit"); tap("Collect credit")
            awaitText("Collect customer credit")
            onView(withHint("Amount (MMK)")).perform(replaceText(id("basePrice").toString()), closeSoftKeyboard())
            onView(withHint("Notes")).perform(replaceText("$tag native credit collection"), closeSoftKeyboard())
            scenario!!.recreate(); awaitText("Collect customer credit")
            onView(withHint("Amount (MMK)")).check(matches(withText(id("basePrice").toString())))
            tap("Record collection")
            awaitCondition("Native collection not reflected") { get("customers", mapOf("search" to tag)).rows("data").single().number("outstanding_amount") == 0L }
            remember("uxCollection", 1)
        }
        if (id("uxExpense") == 0L) {
            open(R.id.trip); awaitText("$tag TEST ONLY route")
            scrollListEnd()
            // Trip actions are before expense history; locate their recycler card directly.
            scenario!!.onActivity { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list).scrollToPosition(2 + methods.size) }
            awaitText("More actions"); tap("More actions")
            onView(withText("Record expense")).perform(click()); awaitText("Record trip expense")
            onView(withHint("Description")).perform(replaceText("$tag native expense"), closeSoftKeyboard())
            onView(withHint("Amount (MMK)")).perform(replaceText("7"), closeSoftKeyboard())
            tap("Save expense")
            awaitCondition("Native expense absent") { get("current-trip").obj("data").rows("expenses").any { it.text("description") == "$tag native expense" } }
            remember("uxExpense", 1)
        }
        if (id("uxCancelled") == 0L) {
            open(R.id.cash); awaitText("Return cash"); tap("Return cash")
            awaitText("Return trip cash")
            onView(withHint("Amount (MMK)")).perform(replaceText("101"), closeSoftKeyboard())
            onView(withHint("Handover note")).perform(replaceText("$tag native cancellation"), closeSoftKeyboard())
            tap("Submit for confirmation")
            awaitCondition("Cash return not created") { get("cash-submissions").rows("data").any { it.text("notes") == "$tag native cancellation" } }
            remember("uxCash", get("cash-submissions").rows("data").first { it.text("notes") == "$tag native cancellation" }.id)
            scenario!!.onActivity { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.list).scrollToPosition(2) }
            awaitText("Cancel handover"); tap("Cancel handover"); awaitText("Cancel cash handover")
            onView(withHint("Cancellation reason")).perform(replaceText("$tag native cancellation check"), closeSoftKeyboard())
            onView(withId(android.R.id.button1)).perform(click())
            awaitCondition("Cash cancellation absent") { get("cash-submissions").rows("data").first { it.id == id("uxCash") }.text("status") == "cancelled" }
            remember("uxCancelled", 1)
        }
        // Verify server replay semantics using the app's actual transport, then delete only this QA draft through native UI.
        if (id("replayDeleted") == 0L) {
            val replay = once("replayDraft") {
                val body = saleBody(method = cash)
                val key = "$tag-create-replay"
                val first = io { repo.transport.send("POST", "api/sales/sales", body, key) }.obj("data")
                val second = io { repo.transport.send("POST", "api/sales/sales", body, key) }.obj("data")
                Assert.assertEquals(first.id, second.id)
                first
            }
            open(R.id.sale_detail, replay); awaitText(get("sales/$replay").obj("data").text("reference"))
            tap("More actions"); onView(withText("Delete draft")).perform(click()); awaitText("Delete this draft?")
            tap("Confirm")
            awaitCondition("Draft not deleted") { runCatching { get("sales/$replay") }.exceptionOrNull().let { it is ApiFailure && it.status == 404 } }
            remember("replayDeleted", 1)
        }
        val voided = once("voidedSale") { command("POST", "sales", saleBody(method = cash)).obj("data") }
        if (get("sales/$voided").obj("data").text("status") == "draft") command("POST", "sales/$voided/post")
        if (get("sales/$voided").obj("data").text("status") == "posted") adminWrite("sales/$voided/void", mapOf("reason" to "$tag void acceptance"))
        Assert.assertEquals("voided", get("sales/$voided").obj("data").text("status"))
        Assert.assertEquals(id("nativeTotal") + id("basePrice") * 2, get("cash-hold").number("cash_hold"))
        remember("uxDone", 1)
        note("Native credit collection with dialog rotation, expense, cash submit/cancel and draft delete passed; live create replay and office void verified")
    }

    private fun profileAndPrinting() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val profile = get("profile").obj("representative")
        awaitText("Hello, ${profile.name}")
        open(R.id.profile); awaitText("Save profile")
        tap("Save profile", true)
        awaitText("Profile updated.")
        Assert.assertTrue(repo.user.value!!.strings("roles").contains("sales-representative"))
        tap("Change password", true); awaitText("Update password")
        onView(withHint("Current password")).perform(replaceText("incorrect-test-password"), closeSoftKeyboard())
        onView(withHint("New password")).perform(replaceText(args.getString("salesPassword")!!), closeSoftKeyboard())
        onView(withHint("Confirm password")).perform(replaceText(args.getString("salesPassword")!!), closeSoftKeyboard())
        tap("Update password")
        awaitCondition("Expected current-password validation") {
            runCatching { onView(withHint("Current password")).check { view, error ->
                if (error != null) throw error
                val wrapper = view.parent.parent as com.google.android.material.textfield.TextInputLayout
                Assert.assertTrue(wrapper.error.toString().contains("current password is incorrect"))
            } }.isSuccess
        }
        onView(withHint("Current password")).perform(replaceText(args.getString("salesPassword")!!), closeSoftKeyboard())
        tap("Update password"); awaitText("Password updated.")
        // Set the same test password: verifies the workflow without changing the user's supplied credential.
        io { repo.login(args.getString("salesLogin")!!, args.getString("salesPassword")!!) }
        val restored = SalesRepository(SecureStore(app))
        Assert.assertTrue(io { restored.restore() })
        Assert.assertEquals(repo.user.value!!.id, restored.user.value!!.id)
        open(R.id.sale_detail, id("nativeSale"))
        awaitText(get("sales/${id("nativeSale")}").obj("data").text("reference"))
        screenshot("posted-sale")
        tap("Print invoice"); awaitText("Print / Save PDF")
        onView(withText("A5")).perform(click()); tap("Print / Save PDF")
        awaitCondition("Android print service did not open") {
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("printspooler") == true
        }
        screenshot("print-preview")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.injectInputEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK), true)
        automation.injectInputEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK), true)
        remember("profileDone", 1)
        note("Native profile save, password validation/update (same test password), encrypted-session restore, and Android A5 print preview passed")
    }

    private fun draftRecovery(restoring: Boolean) {
        if (!restoring) {
            val draft = com.example.superkingsale.sale.SaleDraft(customerId = id("customer"), step = 3,
                notes = "$tag local process-death recovery test", lines = listOf(com.example.superkingsale.sale.SaleLine(
                    productId = id("product"), unitId = id("baseUnit"), focUnitId = id("baseUnit"), quantity = "2")))
            io { repo.draft(Gson().toJson(draft), "sale.0") }
        } else {
            Assert.assertTrue(io { SalesRepository(SecureStore(app)).restore() })
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText("Hello, ${get("profile").obj("representative").name}")
        open(R.id.new_sale); awaitText("Quantities & offers")
        onView(withHint("Quantity")).check(matches(withText("2")))
        if (restoring) {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            fun shell(command: String) { automation.executeShellCommand(command).use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            } }
            try {
                shell("svc wifi disable"); shell("svc data disable")
                awaitText(app.getString(R.string.offline))
                tap("Continue", true); awaitText("Review & submit")
                tap("Save draft", true)
                awaitText("Internet connection is required to complete this transaction.")
                screenshot("offline-draft")
            } finally { shell("svc wifi enable"); shell("svc data enable") }
            awaitCondition("Network did not recover") {
                var online = false; scenario!!.onActivity { online = it.online }; online
            }
            io { repo.draft(null, "sale.0") }
            note("Encrypted draft survived force-stop/relaunch; offline save blocked with input retained; connectivity recovered")
        } else note("Local draft seeded and visible; ready for explicit process termination")
    }

    private fun appearanceAndPrintSizes() {
        app.getSharedPreferences("appearance", 0).edit().putString("language", "en").putInt("theme", -1).putFloat("fontScale", 1f).putString("density", "comfortable").commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitText("Hello, ${get("profile").obj("representative").name}")
        if (args.getString("phase") != "printing") {
        open(R.id.profile); awaitText("Save profile")
        fun choose(hint: String, value: String) {
            onView(withHint(app.tr(hint))).perform(scrollTo(), click())
            screenshot("selector")
            onView(withText(app.tr(value))).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click())
        }
        choose("Language", "မြန်မာ")
        awaitCondition("Myanmar resources did not activate") {
            @Suppress("DEPRECATION")
            var ready = false; scenario!!.onActivity { ready = it.resources.configuration.locale.language == "my" }; ready
        }
        screenshot("myanmar-profile")
        choose("Language", "English")
        onView(withText("Save profile")).perform(scrollTo())
        choose("Theme", "Dark")
        awaitCondition("Dark theme not applied") {
            var dark = false; scenario!!.onActivity { dark = it.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES }; dark
        }
        choose("Text size", "Extra large")
        choose("Density", "Compact")
        screenshot("large-dark-profile")
        choose("Text size", "System default"); choose("Density", "Comfortable"); choose("Theme", "System")
        }
        open(R.id.sale_detail, id("nativeSale"))
        awaitText(get("sales/${id("nativeSale")}").obj("data").text("reference"))
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for ((index, size) in listOf("A4", "A5", "80 mm", "58 mm", "50 mm").withIndex()) {
            tap("Print invoice"); awaitText("Print / Save PDF"); onView(withText(size)).perform(click()); tap("Print / Save PDF")
            awaitCondition("Print UI not open for $size") {
                val root = automation.rootInActiveWindow
                root?.packageName?.toString()?.contains("printspooler") == true
            }
            Thread.sleep(5000) // System print preview rendering is outside Espresso's app idling scope.
            screenshot("print-$index")
            note("Opened Android print preview: $size")
            val cancel = automation.rootInActiveWindow.findAccessibilityNodeInfosByText("Cancel").firstOrNull()
            Assert.assertNotNull("Missing system print Cancel action", cancel)
            Assert.assertTrue(cancel!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            awaitText("Print invoice")
        }
        app.getSharedPreferences("appearance", 0).edit().putString("paper.${repo.user.value!!.id}", "a4").commit()
        note("All five Android print launches passed. Inspect screenshots: the print service can override custom thermal media.")
    }
}

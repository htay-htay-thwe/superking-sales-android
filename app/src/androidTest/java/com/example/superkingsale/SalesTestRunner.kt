package com.example.superkingsale

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.example.superkingsale.data.*
import com.example.superkingsale.sale.SaleDraft
import com.example.superkingsale.sale.SaleLine
import com.google.gson.Gson
import okhttp3.mockwebserver.*
import java.util.concurrent.CopyOnWriteArrayList

class SalesTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, TestSalesApplication::class.java.name, context)
}
class TestSalesApplication : SalesApplication() {
    val store = TestStore()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    var unauthorized = false
    // The mock API is local: ordinary UI tests must not depend on Google's internet probe.
    // This override is not packaged in the real app. Offline protection is tested explicitly.
    var networkAvailable = true
    override fun hasValidatedNetwork() = networkAvailable
    val server = MockWebServer()
    override val repository by lazy { SalesRepository(store, "http://127.0.0.1:${server.port}/public/") }
    override fun onCreate() {
        super.onCreate()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.requestUrl!!.encodedPath.removePrefix("/public/")
                if (unauthorized && path.startsWith("api/sales/")) return MockResponse().setResponseCode(401).setBody("""{"message":"Session expired"}""")
                val body = when {
                    path == "api/branding" -> """{"business_name":"Super King Test"}"""
                    path == "api/sales/me" -> """{"user":{"id":7,"name":"Test Representative","roles":["sales-representative"],"permissions":[]}}"""
                    path == "api/sales/dashboard" -> """{"representative":{"id":7,"name":"Test Representative"},"kpis":{"today_sales":12000,"stock_units":50,"stock_products":1,"cash_hold":12000},"recent_sales":[],"stock":[],"pending_receivings":[]}"""
                    path == "api/sales/current-trip" -> """{"data":$trip}"""
                    path == "api/sales/sale-options" -> """{"trip":$trip,"representative":{"id":7,"regions":[{"id":3,"name":"Yangon"}]},"customers":[$customer],"products":[$product],"payment_methods":[{"key":"cash","name":"Cash","adds_to_cash_hold":true}],"cash_hold":12000}"""
                    path == "api/sales/sale-history-options" -> """{"trips":[$trip],"customers":[$customer],"products":[$product]}"""
                    path == "api/sales/sales/44" && request.method == "PUT" -> """{"data":$sale}"""
                    path == "api/sales/sales/44/post" -> """{"data":${sale.replace("\"draft\"", "\"posted\"")}}"""
                    path == "api/sales/sales/44" -> """{"data":$sale}"""
                    path == "api/sales/sales" -> """{"data":[$sale],"meta":$meta,"summary":{"gross_sales":1000,"cash_sales":1000,"credit_sales":0,"units_sold":2}}"""
                    path == "api/sales/cash-hold" -> """{"cash_hold":12000,"pending_submissions":0,"available_to_submit":12000}"""
                    path == "api/sales/cash-submissions" && request.method == "POST" -> """{"data":{"id":8,"reference":"CASH-008","amount":12000,"status":"pending"}}"""
                    path == "api/sales/cash-submissions" || path == "api/sales/cash-transactions" -> """{"data":[],"meta":$meta}"""
                    path == "api/sales/customer-options" -> """{"regions":[{"id":3,"name":"Yangon"}],"payment_methods":[{"key":"cash","name":"Cash","adds_to_cash_hold":true}]}"""
                    path == "api/sales/customers" -> """{"data":[$customer],"meta":$meta}"""
                    path == "api/sales/profile" -> """{"representative":{"id":7,"name":"Test Representative","code":"REP-007","primary_warehouse":{"name":"Main warehouse"},"account":{"name":"Test Representative","username":"rep","email":"rep@example.test"}}}"""
                    path == "api/sales/stock" -> """{"data":[{"id":1,"product":$product,"quantity":50,"foc_quantity":5,"pending_quantity":0}],"meta":$meta,"summary":{"on_hand":50,"foc_on_hand":5,"incoming":0}}"""
                    path == "api/sales/receivings" -> """{"data":[$transfer],"meta":$meta}"""
                    path == "api/sales/receiving-history" -> """{"data":[],"meta":$meta}"""
                    path == "api/sales/receivings/9" -> """{"data":$transfer}"""
                    else -> return MockResponse().setResponseCode(404).setBody("""{"message":"Unexpected test route: $path"}""")
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try { executor.submit { server.start(java.net.InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), 0) }.get() } finally { executor.shutdown() }
        seed()
    }
    fun seed() {
        unauthorized = false; networkAvailable = true; requests.clear()
        store.put("user", """{"id":7}""")
        store.put("draft.7.sale.0", Gson().toJson(SaleDraft(id = 44, reference = "SAL-044", customerId = 10, lines = listOf(SaleLine(1, 2)))))
    }
    companion object {
        val meta = """{"current_page":1,"last_page":1,"total":1}"""
        val customer = """{"id":10,"code":"CUS-010","name":"Test Shop","is_active":true,"credit_allowed":true,"credit_limit":50000,"available_credit":45000,"outstanding_amount":5000,"region":{"id":3,"name":"Yangon"}}"""
        val product = """{"id":1,"sku":"SKU-001","name":"Test Product","unit":"piece","quantity":50,"foc_quantity":5,"units":[{"id":2,"name":"piece","conversion_factor":1,"is_default_selling":true,"prices":[{"region_id":3,"price":500}]}]}"""
        val trip = """{"id":5,"reference":"TRIP-005","title":"Yangon route","status":"operation","region":{"id":3,"name":"Yangon"},"warehouse":{"name":"Main warehouse"},"vehicle":{"vehicle_number":"TEST-1","vehicle_type":"Van"},"financial_summary":{"current_cash_hold":12000,"cash_sales":12000},"sales":[],"expenses":[]}"""
        val sale = """{"id":44,"reference":"SAL-044","customer":$customer,"status":"draft","payment_type":"cash","payment_method":"cash","payment_method_name":"Cash","total_amount":1000,"gross_amount":1000,"items":[{"id":1,"product":$product,"quantity":2,"unit":{"id":2,"name":"piece"},"unit_price":500,"line_total":1000}]}"""
        val transfer = """{"id":9,"reference":"REP-009","status":"dispatched","source_warehouse":{"name":"Main warehouse"},"items":[{"id":1,"product":$product,"quantity":5,"base_quantity":5,"unit":{"name":"piece"},"foc_quantity":1,"foc_unit":{"name":"piece"}}]}"""
    }
}
class TestStore : PrivateStore {
    private val values = java.util.concurrent.ConcurrentHashMap<String, String>()
    override fun get(name: String) = values[name]
    override fun put(name: String, value: String?) { if (value == null) values.remove(name) else values[name] = value }
    override fun clear() = values.clear()
}

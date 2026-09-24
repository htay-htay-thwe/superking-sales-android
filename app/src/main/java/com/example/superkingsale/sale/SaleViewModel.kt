package com.example.superkingsale.sale

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.superkingsale.data.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class SaleState(
    val loading: Boolean = true, val busy: Boolean = false, val error: String = "",
    val fields: Map<String, String> = emptyMap(), val options: Record = Record(),
    val draft: SaleDraft = SaleDraft(), val version: Int = 0,
    val confirmSale: Record? = null, val completed: Long? = null, val notice: String = "",
)
class SaleViewModel(private val repo: SalesRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val gson = com.google.gson.GsonBuilder().setObjectToNumberStrategy(com.google.gson.ToNumberPolicy.BIG_DECIMAL).create()
    private val _state = MutableStateFlow(SaleState())
    val state = _state.asStateFlow()
    private val editId: Long = saved["recordId"] ?: 0
    private var saveJob: Job? = null
    private var loadJob: Job? = null
    private var loadedRevision = -1L
    private val slot get() = "sale.$editId"
    init { load() }
    fun load() {
        if (loadJob?.isActive == true || state.value.busy || state.value.confirmSale != null) return
        loadJob = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = "") }
        try {
            val options = repo.get("sale-options")
            loadedRevision = repo.revisions.value
            var draft = if (_state.value.version > 0) _state.value.draft
                else saved.get<String>("draft")?.let { gson.fromJson(it, SaleDraft::class.java) }
                    ?: repo.draft(slot)?.let { gson.fromJson(it, SaleDraft::class.java) } ?: SaleDraft()
            if (editId > 0 && draft.id != editId) {
                val r = repo.get("sales/$editId").obj("data")
                if (r.text("status") != "draft") throw ApiFailure("Only draft sales can be edited.", 409)
                draft = SaleDraft(id = r.id, reference = r.text("reference"), customerId = r.obj("customer").id,
                    paymentType = r.text("payment_type"), paymentMethod = r.text("payment_method", "cash"),
                    notes = r.text("notes"), cashback = r.number("cashback_amount").toString(),
                    invoicePromotion = r.number("promotion_amount"), invoicePromotionTitle = r.text("promotion_title"),
                    lines = r.rows("items").map { line ->
                        SaleLine(line.obj("product").id, line.obj("unit").id, line.number("quantity").toString(),
                            line.obj("foc_unit").id, line.number("foc_quantity").toString(), line.text("discount_percentage", "0"),
                            line.text("promotion_title"), line.number("promotion_amount").toString())
                    })
            }
            if (options.rows("payment_methods").none { it.text("key") == draft.paymentMethod }) {
                draft = draft.copy(paymentMethod = options.rows("payment_methods").firstOrNull()?.text("key").orEmpty())
            }
            _state.update { it.copy(loading = false, options = options, draft = draft, version = it.version + 1) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.update { it.copy(loading = false, error = e.message.orEmpty()) } }
        }
    }
    fun change(structural: Boolean = false, block: (SaleDraft) -> SaleDraft) {
        if (_state.value.busy || _state.value.draft.pendingCreateJson != null) return
        _state.update { it.copy(draft = block(it.draft), version = it.version + if (structural) 1 else 0, error = "", fields = emptyMap()) }
        persist()
    }
    private fun persist() {
        val encoded = gson.toJson(_state.value.draft)
        saved["draft"] = encoded
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(250); repo.draft(encoded, slot) }
    }
    fun line(id: Long, block: (SaleLine) -> SaleLine) = change { d -> d.copy(lines = d.lines.map { if (it.productId == id) block(it) else it }) }
    fun refreshIfChanged() {
        if (!state.value.loading && !state.value.busy && loadedRevision != repo.revisions.value) load()
    }
    fun toggle(product: Record) {
        if (state.value.draft.lines.size >= 100 && state.value.draft.lines.none { it.productId == product.id }) {
            error("A sale can contain at most 100 products."); return
        }
        change(true) { d ->
        d.copy(lines = if (d.lines.any { it.productId == product.id }) d.lines.filterNot { it.productId == product.id }
        else d.lines + SaleLine(productId = product.id, unitId = defaultUnit(product).id, focUnitId = defaultUnit(product).id))
        }
    }
    fun defaultUnit(product: Record) = product.rows("units").find { it.flag("is_default_selling") } ?: product.rows("units").firstOrNull() ?: Record()
    fun product(line: SaleLine) = state.value.options.rows("products").find { it.id == line.productId } ?: Record()
    fun unit(line: SaleLine, foc: Boolean = false) = product(line).rows("units").find { it.id == if (foc && line.focUnitId > 0) line.focUnitId else line.unitId } ?: defaultUnit(product(line))
    fun customer() = state.value.options.rows("customers").find { it.id == state.value.draft.customerId } ?: Record()
    fun price(line: SaleLine): Long? = unit(line).rows("prices").find { it.number("region_id") == customer().obj("region").id }?.number("price")
    fun total(line: SaleLine): Long = runCatching { SaleMath.lineTotal(price(line) ?: 0, line.quantity.toLongOrNull() ?: 0,
        line.discount.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO, line.promotion.toLongOrNull() ?: 0) }.getOrDefault(0)
    fun payable(): Long = state.value.draft.lines.sumOf(::total) - (state.value.draft.cashback.toLongOrNull() ?: 0) - state.value.draft.invoicePromotion

    private fun validate(step: Int): String? {
        val d = state.value.draft
        if (step == 1) {
            if (customer().empty) return "Select an active customer in the trip region."
            if (d.paymentType == "credit" && !customer().flag("credit_allowed")) return "This customer is cash-only."
            if (d.paymentType == "cash" && state.value.options.rows("payment_methods").none { it.text("key") == d.paymentMethod }) return "Select a payment method."
            if (d.id == 0L && d.latitude == null) return "Capture your current location before continuing."
        }
        if (step >= 2 && d.lines.isEmpty()) return "Select at least one product."
        if (step >= 3) {
            for (line in d.lines) {
                val product = product(line)
                val name = product.name
                if (product.empty || price(line) == null) return "A selected product or unit has no active regional price. Refresh availability."
                val quantity = SaleMath.whole(line.quantity, 1) ?: return "$name: enter a whole quantity of at least 1."
                val foc = SaleMath.whole(line.focQuantity) ?: return "$name: enter a whole FOC quantity."
                if (java.math.BigInteger.valueOf(quantity).multiply(java.math.BigInteger.valueOf(unit(line).number("conversion_factor"))) > java.math.BigInteger.valueOf(product.number("quantity")))
                    return "$name: quantity exceeds available paid stock."
                if (java.math.BigInteger.valueOf(foc).multiply(java.math.BigInteger.valueOf(unit(line, true).number("conversion_factor"))) > java.math.BigInteger.valueOf(product.number("foc_quantity")))
                    return "$name: FOC quantity exceeds available FOC stock."
                if (SaleMath.discount(line.discount) == null) return "$name: discount must be 0–100 with at most two decimal places."
                if (SaleMath.whole(line.promotion) == null || total(line) < 0) return "$name: reductions cannot exceed the line total."
            }
        }
        if (step == 4) {
            if (SaleMath.whole(d.cashback) == null || payable() < 0) return "Cashback cannot exceed the merchandise subtotal."
            if (d.paymentType == "credit" && payable() > customer().number("available_credit")) return "The sale exceeds this customer's remaining credit."
        }
        return null
    }
    fun next() {
        validate(state.value.draft.step)?.let { error(it); return }
        change(true) { it.copy(step = minOf(4, it.step + 1)) }
    }
    fun back() = change(true) { it.copy(step = maxOf(1, it.step - 1)) }
    fun error(message: String) { _state.update { it.copy(error = message) } }
    fun location(lat: Double, lon: Double, accuracy: Float) = change(true) {
        it.copy(latitude = lat, longitude = lon, accuracy = accuracy, capturedAt = System.currentTimeMillis())
    }
    fun save(askPost: Boolean) {
        if (state.value.busy || state.value.loading) return
        val initial = state.value.draft
        if (initial.pendingCreateJson == null) {
            (1..4).forEach { step -> validate(step)?.let { error(it); return } }
            if (initial.id == 0L && System.currentTimeMillis() - initial.capturedAt > 120000) {
                error("Location is older than two minutes. Capture your location again before saving."); return
            }
        }
        _state.update { it.copy(busy = true, error = "", notice = "", fields = emptyMap()) }
        viewModelScope.launch {
            try {
                val d = state.value.draft
                val body: Map<String, Any?> = if (d.pendingCreateJson != null) gson.fromJson(d.pendingCreateJson, object: TypeToken<Map<String, Any?>>() {}.type)
                else linkedMapOf<String, Any?>(
                    "customer_id" to d.customerId, "payment_type" to d.paymentType, "payment_method" to d.paymentMethod,
                    "notes" to d.notes, "cashback_amount" to d.cashback.toLong(), "promotion_amount" to d.invoicePromotion, "promotion_title" to d.invoicePromotionTitle,
                    "items" to d.lines.map { l -> linkedMapOf("product_id" to l.productId, "product_unit_id" to unit(l).id,
                        "quantity" to l.quantity.toLong(), "discount_percentage" to l.discount.toBigDecimal(),
                        "promotion_title" to l.promotionTitle, "promotion_amount" to l.promotion.toLong(),
                        "foc_product_unit_id" to unit(l, true).id, "foc_quantity" to l.focQuantity.toLong()) }
                ).apply {
                    if (d.id == 0L) { put("creation_latitude", d.latitude); put("creation_longitude", d.longitude); put("location_accuracy_meters", d.accuracy) }
                }
                // Persist the exact create body before touching the network; retries do not acquire a new location.
                if (d.id == 0L) {
                    val locked = d.copy(pendingCreateJson = gson.toJson(body))
                    _state.update { it.copy(draft = locked) }; saved["draft"] = gson.toJson(locked)
                    saveJob?.cancel(); repo.draft(gson.toJson(locked), slot)
                }
                val response = repo.command(if (d.id > 0) "PUT" else "POST", if (d.id > 0) "sales/${d.id}" else "sales", body)
                val sale = response.obj("data")
                _state.update { it.copy(busy = false, draft = it.draft.copy(id = sale.id, reference = sale.text("reference"), pendingCreateJson = null),
                    confirmSale = if (askPost) sale else null, version = it.version + 1,
                    notice = if (askPost) "" else "${sale.text("reference")} saved as draft.") }
                persist()
            } catch (e: CancellationException) { throw e }
            catch (e: ApiFailure) {
                _state.update { it.copy(busy = false, error = e.message, fields = e.fields,
                    draft = if (!e.uncertain && e.status != 0) it.draft.copy(pendingCreateJson = null) else it.draft, version = it.version + 1) }
                persist()
            } catch (e: Exception) { _state.update { it.copy(busy = false, error = e.message.orEmpty(), version = it.version + 1) } }
        }
    }
    fun dismissConfirm() { _state.update { it.copy(confirmSale = null) } }
    fun post() {
        if (state.value.busy || state.value.loading) return
        val id = state.value.draft.id
        if (id == 0L) return
        _state.update { it.copy(busy = true, confirmSale = null, error = "") }
        viewModelScope.launch {
            try {
                repo.command("POST", "sales/$id/post")
                saveJob?.cancel(); repo.draft(null, slot); saved["draft"] = null
                _state.update { it.copy(busy = false, completed = id) }
            } catch (e: ApiFailure) { _state.update { it.copy(busy = false, error = e.message, fields = e.fields) } }
            catch (e: Exception) { _state.update { it.copy(busy = false, error = e.message.orEmpty()) } }
        }
    }
    fun reset() {
        saveJob?.cancel()
        _state.update { it.copy(draft = SaleDraft(), completed = null, confirmSale = null, version = it.version + 1) }
        saved["draft"] = null
        viewModelScope.launch { repo.draft(null, slot) }
    }
    fun createdCustomer(id: Long) = viewModelScope.launch {
        try {
            val options = repo.get("sale-options")
            _state.update { it.copy(options = options, draft = it.draft.copy(customerId = id, paymentType = "cash"), version = it.version + 1) }
            persist()
        } catch (e: Exception) { error(e.message.orEmpty()) }
    }
}

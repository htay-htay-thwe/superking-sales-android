package com.example.superkingsale.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.superkingsale.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class WorkspaceState(
    val loading: Boolean = false, val busy: Boolean = false, val error: String = "",
    val data: Record = Record(), val extra: Record = Record(), val options: Record = Record(),
    val version: Long = 0, val notice: String = "", val fields: Map<String, String> = emptyMap(),
    val navigation: String = "", val resultId: Long = 0,
)
class WorkspaceViewModel(
    private val repo: SalesRepository, val saved: SavedStateHandle,
) : ViewModel() {
    val screen: String get() = saved["screen"] ?: "home"
    val id: Long get() = saved["recordId"] ?: 0L
    val page: Int get() = saved["page"] ?: 1
    val tab: String get() = saved["tab"] ?: if (screen == "cash") "returns" else "stock"
    val scope: String get() = saved["scope"] ?: "trip"
    var query: Map<String, String>
        get() = (saved.get<HashMap<String, String>>("query") ?: hashMapOf()).toMap()
        set(value) { saved["query"] = HashMap(value) }
    private val _state = MutableStateFlow(WorkspaceState())
    val state = _state.asStateFlow()
    private var job: Job? = null
    private var revision = -1L
    init { load() }
    fun refreshIfChanged() { if (revision != repo.revisions.value && !_state.value.busy && !_state.value.loading) load() }
    fun tab(value: String) { saved["tab"] = value; saved["page"] = 1; load() }
    fun scope(value: String) { saved["scope"] = value; saved["page"] = 1; load() }
    fun page(value: Int) { saved["page"] = value; load() }
    fun filter(value: Map<String, String>) { query = value; saved["page"] = 1; load() }
    fun load() {
        if (_state.value.busy) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "", fields = emptyMap()) }
            try {
                val params = query + mapOf("page" to page.toString(), "per_page" to "20")
                var extra = Record(); var options = Record()
                val data = when (screen) {
                    "home" -> repo.get("dashboard")
                    "trip" -> repo.get("current-trip")
                    "stock" -> coroutineScope {
                        val inventory = async { repo.get(when(tab) { "pending" -> "receivings"; "history" -> "receiving-history"; else -> "stock" }, params) }
                        val pending = async { repo.get("receivings", mapOf("per_page" to "10")) }
                        val summary = if (tab == "stock") null else async { repo.get("stock", mapOf("per_page" to "10")) }
                        extra = pending.await()
                        options = summary?.await() ?: Record()
                        inventory.await()
                    }
                    "receiving" -> repo.get("receivings/$id")
                    "sales" -> {
                        repo.get("sales", params)
                    }
                    "sale_detail" -> repo.get("sales/$id")
                    "cash" -> {
                        extra = repo.get("current-trip").obj("data")
                        options = repo.get("cash-hold")
                        if (tab == "ledger") repo.get("cash-transactions", params)
                        else if (scope == "trip" && extra.empty) Record.parse("{\"data\":[]}")
                        else repo.get("cash-submissions", params + if (scope == "trip") mapOf("trip_id" to extra.id.toString()) else emptyMap())
                    }
                    "customers" -> { options = repo.get("customer-options"); extra = repo.get("current-trip").obj("data"); repo.get("customers", params) }
                    "customer_new" -> repo.get("customer-options")
                    "profile" -> repo.get("profile")
                    else -> Record()
                }
                revision = repo.revisions.value
                _state.update { it.copy(loading = false, data = data, extra = extra, options = options, version = it.version + 1) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(loading = false, error = e.message ?: "Unable to load.") } }
        }
    }
    fun command(method: String, path: String, body: Map<String, Any?> = emptyMap(),
        idempotent: Boolean = true, notice: String = "Saved.", destination: String = "") {
        if (_state.value.busy || _state.value.loading) return
        _state.update { it.copy(busy = true, error = "", fields = emptyMap(), notice = "") }
        viewModelScope.launch {
            try {
                val result = repo.command(method, path, body, idempotent)
                _state.update { it.copy(busy = false, notice = notice, navigation = destination, resultId = result.obj("customer").id) }
                if (destination.isBlank()) load()
            } catch (e: CancellationException) { throw e }
            catch (e: ApiFailure) {
                _state.update { it.copy(busy = false, error = e.message, fields = e.fields) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.message ?: "Unable to save.") }
            }
        }
    }
    fun navigated() { _state.update { it.copy(navigation = "", resultId = 0) } }
}

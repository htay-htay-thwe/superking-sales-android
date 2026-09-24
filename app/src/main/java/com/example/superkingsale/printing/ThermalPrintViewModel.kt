package com.example.superkingsale.printing

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.superkingsale.SalesApplication
import com.example.superkingsale.data.Record
import com.example.superkingsale.ui.tr
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PrintState(val busy: Boolean = false, val ready: Boolean = false, val message: String = "Loading…")
class ThermalPrintViewModel(private val app: SalesApplication, private val saved: SavedStateHandle) : ViewModel() {
    private val journal = app.getSharedPreferences("printer_job", 0)
    private val mutable = MutableStateFlow(PrintState(message = if (journal.getBoolean("inFlight", false))
        "Printing was interrupted. Some paper may already have printed. Check the printer before sending again." else "Loading…"))
    val state = mutable.asStateFlow()
    private var sale = Record()
    private var branding = Record()
    private var loaded = false
    private var saleId = 0L
    private var transport: PrinterTransport? = null
    private var job: Job? = null
    fun load(id: Long) {
        if (loaded || job?.isActive == true) return
        saleId = id
        job = viewModelScope.launch {
            try {
                sale = if (id > 0) app.repository.get("sales/$id").obj("data") else Record()
                branding = app.repository.branding; loaded = true
                mutable.value = PrintState(ready = true, message = if (journal.getBoolean("inFlight", false))
                    "Printing was interrupted. Some paper may already have printed. Check the printer before sending again."
                    else "Ready. Print one test receipt before printing invoices.")
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = PrintState(message = e.message ?: "Unable to load invoice.") }
        }
    }
    fun message(value: String) { if (!mutable.value.busy) mutable.value = mutable.value.copy(message = value) }
    fun print(target: PrinterTarget, dots: Int, large: Boolean, cut: Boolean, test: Boolean) {
        if (mutable.value.busy || !loaded) return
        if (!journal.edit().putBoolean("inFlight", true).commit()) {
            message("Could not persist print job state. Nothing was sent."); return
        }
        mutable.value = PrintState(busy = true, ready = true, message = "Sending to printer…")
        job = viewModelScope.launch {
            val sender = PrinterTransport(app); transport = sender
            val deadline = launch { delay(60000); sender.close() }
            try {
                withContext(Dispatchers.IO) {
                    sender.use { it.send(target, ReceiptDocument(sale, branding, dots, app::tr, test, large).rasterChunks(), cut) { _ -> } }
                }
                mutable.value = PrintState(ready = true, message = "Data sent. Verify the paper output; the printer has not acknowledged completion.")
                journal.edit().putBoolean("inFlight", false).commit()
            } catch (e: CancellationException) {
                mutable.value = PrintState(ready = true, message = "Printing cancelled. Some paper may already have printed. Check before retrying.")
                throw e
            } catch (e: Exception) {
                mutable.value = PrintState(ready = true, message = "Print failed. Check the connection and paper before retrying; a partial receipt may have printed.")
            } finally { sender.close(); deadline.cancel(); transport = null }
        }
    }
    fun prepareExport(paper: Int, dots: Int, large: Boolean, test: Boolean): String {
        saved["export.paper"] = paper; saved["export.dots"] = dots; saved["export.large"] = large; saved["export.test"] = test
        return (if (test) "SuperKing-test" else sale.text("reference", "invoice").replace(Regex("[^A-Za-z0-9_-]"), "_")) + "-$paper-mm.pdf"
    }
    fun export(uri: Uri) {
        if (mutable.value.busy || !loaded) { message("Invoice is loading. Retry PDF export when ready."); return }
        val paper = saved.get<Int>("export.paper") ?: return
        val dots = saved.get<Int>("export.dots") ?: return
        val large = saved.get<Boolean>("export.large") ?: false
        val test = saved.get<Boolean>("export.test") ?: (saleId == 0L)
        mutable.value = PrintState(busy = true, ready = true, message = "Saving PDF…")
        job = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "w")?.use { ReceiptDocument(sale, branding, dots, app::tr, test, large).pdf(it, paper) }
                        ?: error("Unable to open document")
                }
                mutable.value = PrintState(ready = true, message = "Exact-width PDF saved. When printing it, disable scaling and select matching paper.")
            } catch (e: CancellationException) {
                mutable.value = PrintState(ready = true, message = "PDF export cancelled. Check the selected document for a partial file.")
                throw e
            } catch (_: Exception) { mutable.value = PrintState(ready = true, message = "PDF could not be saved. Remove any partial file and retry.") }
        }
    }
    fun cancel() { transport?.close(); job?.cancel() }
    override fun onCleared() { transport?.close() }
}

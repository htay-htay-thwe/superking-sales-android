package com.example.superkingsale.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.text.InputType
import android.content.Intent
import android.net.Uri
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.superkingsale.*
import com.example.superkingsale.data.*
import com.example.superkingsale.databinding.FragmentWorkspaceBinding
import com.example.superkingsale.ui.LocalizedDialogBuilder as MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class WorkspaceFragment : Fragment(R.layout.fragment_workspace) {
    private val host get() = requireActivity() as MainActivity
    private val vm: WorkspaceViewModel by viewModels {
        viewModelFactory { initializer { WorkspaceViewModel(host.repository, createSavedStateHandle()) } }
    }
    private var binding: FragmentWorkspaceBinding? = null
    private val b get() = checkNotNull(binding)
    private val cards = CardAdapter(action = ::act)
    private var rendered = -1L
    private var notice = ""
    private var dialog: androidx.appcompat.app.AlertDialog? = null
    private var keepDialog = false
    private var dialogError: android.widget.TextView? = null
    private var dialogFields = mutableMapOf<String, com.google.android.material.textfield.TextInputEditText>()
    private val locationCapture = com.example.superkingsale.location.ForegroundLocationCapture(this)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentWorkspaceBinding.bind(view); rendered = -1
        if (vm.screen in listOf("home", "trip")) {
            val gutter = resources.getDimensionPixelSize(R.dimen.foundation_home_gutter)
            b.list.setPaddingRelative(gutter, b.list.paddingTop, gutter, b.list.paddingBottom)
        }
        b.list.layoutManager = LinearLayoutManager(requireContext()); b.list.adapter = cards
        cards.controls = ::inlineControls
        b.retry.setOnClickListener { vm.load() }
        b.configurePullRefresh({ !vm.state.value.loading && !vm.state.value.busy && dialog == null }) { vm.load() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { s ->
                    b.progress.isVisible = s.busy
                    b.swipeRefresh.isEnabled = !s.busy
                    b.swipeRefresh.isRefreshing = s.loading
                    val error = s.error + if (s.fields.isNotEmpty()) "\n" + s.fields.values.joinToString("\n") else ""
                    b.status.isVisible = error.isNotBlank(); b.status.text = requireContext().tr(error)
                    b.retry.isVisible = s.error.isNotBlank() && !s.busy
                    cards.busy = s.busy || s.loading
                    dialog?.getButton(-1)?.isEnabled = !s.busy
                    dialog?.getButton(-2)?.isEnabled = !s.busy
                    dialog?.setCancelable(!s.busy)
                    dialogError?.text = requireContext().tr(error)
                    dialogFields.forEach { (key, edit) -> (edit.parent.parent as? com.google.android.material.textfield.TextInputLayout)?.error = s.fields[key] }
                    if (s.version != rendered && !s.data.empty) { rendered = s.version; render(s) }
                    b.controls.enableChildren(!s.busy && !s.loading)
                    b.form.enableChildren(!s.busy && !s.loading)
                    b.list.enableChildren(!s.busy && !s.loading)
                    b.retry.isEnabled = !s.loading && !s.busy
                    if (s.notice.isNotBlank() && s.notice != notice) {
                        notice = s.notice; dialog?.dismiss(); dialog = null
                        Snackbar.make(b.root, requireContext().tr(s.notice), Snackbar.LENGTH_LONG).show()
                    }
                    if (s.navigation.isNotBlank()) {
                        vm.navigated()
                        when (s.navigation) {
                            "home" -> open(R.id.home)
                            "customer" -> {
                                findNavController().previousBackStackEntry?.savedStateHandle?.set("createdCustomer", s.resultId)
                                findNavController().popBackStack()
                            }
                            "back" -> findNavController().popBackStack()
                        }
                    }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); vm.refreshIfChanged() }
    override fun onDestroyView() { keepDialog = true; dialog?.dismiss(); dialog = null; binding = null; super.onDestroyView() }
    private fun open(dest: Int, id: Long = 0) = host.open(dest, id)
    private fun action(label: String, key: String, id: Long = 0) = CardAction(label, key, id)
    private fun record(): Record = vm.state.value.data.obj("data")
    private fun refreshControls() {
        b.controls.removeAllViews()
        b.controls.isVisible = false
    }
    private fun inlineControls(container: LinearLayout) {
        container.surface()
        if (vm.screen in listOf("stock", "sales", "customers")) {
            val grid = container.grid(150, 2)
            val search = grid.cell().field("Search", vm.saved["searchDraft"] ?: vm.query["search"].orEmpty()) { vm.saved["searchDraft"] = it }
            search.filters = arrayOf(android.text.InputFilter.LengthFilter(100))
            grid.cell().button(if (vm.screen == "sales") "Apply" else "Search") { vm.filter(vm.query + ("search" to search.text.toString())) }.outlined()
            if (vm.screen == "sales") container.button("Filters") { filters() }.outlined()
        }
        if (vm.screen == "stock") container.options("Stock view", listOf("stock" to "On hand", "pending" to "Pending receiving", "history" to "Issue history"), vm.tab, vm::tab)
        if (vm.screen == "cash") {
            container.options("Cash history", listOf("returns" to "Cash returns", "ledger" to "Cash ledger"), vm.tab, vm::tab)
            if (vm.tab == "returns") container.options("Scope", listOf("trip" to "This trip", "all" to "All history"), vm.scope, vm::scope)
        }
    }
    private fun render(s: WorkspaceState) {
        val form = vm.screen in listOf("customer_new", "profile")
        b.list.isVisible = !form; b.formScroll.isVisible = form
        refreshControls()
        if (form) { renderForm(s); return }
        val items = mutableListOf<Card>()
        when (vm.screen) {
            "home" -> {
                val k = s.data.obj("kpis")
                items += Card("welcome", "Hello, ${s.data.obj("representative").name}", money(k.number("today_sales")),
                    "Today's sales\nCash ${money(k.number("today_cash_sales"))} · Credit ${money(k.number("today_credit_sales"))}",
                    listOf(action("New sale", "new_sale")))
                items += Card("stock", "Stock in your custody", number(k.number("stock_units")) + " units",
                    "${k.number("stock_products")} products · ${k.number("pending_receivings")} pending receivings",
                    listOf(action("My stock & receiving", "stock")))
                items += Card("cash", "Cash hold", money(k.number("cash_hold")), "Balance changes after office confirmation",
                    listOf(action("View cash", "cash"), action("Customers & credit collection", "customers")))
                s.data.rows("recent_sales").forEach { items += saleCard(it) }
                s.data.rows("pending_receivings").forEach { r -> items += Card("receive${r.id}", r.text("reference"),
                    "${r.number("total_quantity")} units", r.obj("warehouse").name, listOf(action("Check & receive", "receiving", r.id))) }
                s.data.rows("stock").forEach { items += stockCard(it) }
                items += Card("asof", "Last updated", detail = s.data.text("as_of"), actions = listOf(action("View sales history", "sales")))
            }
            "trip" -> {
                val trip = record()
                if (trip.empty) items += Card("empty", "No active trip", detail = "The office must create a trip and issue stock before selling begins.")
                else {
                    val f = trip.obj("financial_summary")
                    items += Card("trip", trip.name, trip.text("reference"), listOf(trip.text("status").uppercase(),
                        trip.obj("region").name, trip.obj("warehouse").name, trip.obj("vehicle").text("vehicle_number"),
                        trip.obj("vehicle").text("vehicle_type"), trip.text("notes")).filter { it.isNotBlank() }.joinToString(" · "))
                    items += Card("balance", "Trip position", money(f.number("current_cash_hold")),
                        "Stock held: ${number(trip.number("current_stock_units"))}\nPaid sales: ${money(f.number("cash_sales"))}\nCredit sales: ${money(f.number("credit_sales"))}\nLatest credit: ${money(f.number("latest_credit_balance"))}\nExpenses: ${money(f.number("expenses"))}\nConfirmed returns: ${money(f.number("cash_submitted_confirmed"))}\nPending returns: ${money(f.number("cash_submitted_pending"))}",
                        listOf(action("View stock", "stock"), action("Return cash", "cash")))
                    f.rows("payment_method_totals").forEach { m -> items += Card("method${m.text("key")}", m.name, money(m.number("total_amount")),
                        "Sales: ${money(m.number("sales_amount"))}\nCredit collected: ${money(m.number("collection_amount"))}\n" +
                            if (m.flag("adds_to_cash_hold")) "Included in cash hold" else "Direct / banking") }
                    if (trip.text("status") == "operation") items += Card("commands", "Trip actions", actions =
                        listOf(action("New sale", "new_sale"), action("Record expense", "expense", trip.id), action("Begin trip ending", "end", trip.id)))
                    if (trip.text("status") == "ending") items += Card("ending", "Trip is ending", detail = "Return remaining stock and submit cash. The office will close the trip.")
                    trip.rows("sales").take(5).forEach { items += saleCard(it) }
                    trip.rows("expenses").forEach { e -> items += Card("expense${e.id}", e.text("description"), money(e.number("amount")),
                        e.text("spent_at") + "\n" + e.text("notes")) }
                }
            }
            "stock" -> {
                if (vm.tab == "stock") {
                    val summary = s.data.obj("summary")
                    items += Card("summary", "Stock balance", "${summary.number("on_hand")} paid units", "FOC: ${summary.number("foc_on_hand")} · Incoming: ${summary.number("incoming")}")
                    s.data.rows("data").forEach { items += stockCard(it) }
                } else s.data.rows("data").forEach { r ->
                    items += Card("transfer${r.id}", r.text("reference"), r.text("status").uppercase(),
                        "${r.obj("source_warehouse").name}\nPaid: ${r.number("total_quantity")} · FOC base units: ${r.number("total_foc_base_quantity")}\n${r.obj("trip").text("reference")}\n${r.text("dispatched_at")}",
                        listOf(action("View receiving", "receiving", r.id)))
                }
            }
            "receiving" -> {
                val r = record()
                items += Card("transfer", r.text("reference"), r.text("status").uppercase(),
                    "${r.obj("source_warehouse").name}\n${r.obj("trip").text("reference")}\n${r.text("notes")}")
                r.rows("items").forEach { i -> items += Card("item${i.id}", i.obj("product").name,
                    "${i.number("quantity")} ${i.obj("unit").name}",
                    "SKU: ${i.obj("product").text("sku")}\nBase units: ${i.number("base_quantity")}\nFOC: ${i.number("foc_quantity")} ${i.obj("foc_unit").name}\nIn transit: ${i.number("in_transit_quantity")}") }
                items += Card("audit", "Receiving record", detail = audit(r))
                if (r.text("status") == "dispatched") items += Card("approve", "Physical stock check",
                    detail = "Confirm all paid and FOC quantities match. Partial receiving is not supported.",
                    actions = listOf(action("Receive all stock", "receive", r.id)))
            }
            "sales" -> {
                val summary = s.data.obj("summary")
                items += Card("summary", "Posted sales", money(summary.number("gross_sales")),
                    "Cash: ${money(summary.number("cash_sales"))}\nCredit: ${money(summary.number("credit_sales"))}\nUnits sold: ${number(summary.number("units_sold"))}\n" +
                        vm.query.entries.joinToString(" · ") { "${it.key}: ${it.value}" },
                    listOf(action("New sale", "new_sale")))
                s.data.rows("data").forEach { items += saleCard(it, true) }
            }
            "sale_detail" -> {
                val r = record()
                items += Card("sale", r.text("reference"), money(r.number("total_amount")),
                    "${r.text("status").uppercase()} · ${r.text("payment_type")} · ${r.text("payment_method_name")}\n${r.obj("customer").name}\n${r.obj("customer").text("phone")}\n${r.obj("customer").text("address")}\nTrip: ${r.obj("trip").text("reference")}\n${r.obj("region").name}",
                    if (r.text("status") == "draft") listOf(action("Edit draft", "edit", r.id), action("Post draft", "post", r.id), action("Delete draft", "delete", r.id), action("Print invoice", "print", r.id))
                    else listOf(action("Print invoice", "print", r.id)))
                r.rows("items").forEach { i -> items += Card("item${i.id}", i.obj("product").name, money(i.number("line_total")),
                    "${i.number("quantity")} ${i.obj("unit").name} × ${money(i.number("unit_price"))}\nFOC: ${i.number("foc_quantity")} ${i.obj("foc_unit").name}\nDiscount: ${i.text("discount_percentage")} % · ${money(i.number("discount_amount"))}\n${i.text("promotion_title", "Promotion")}: ${money(i.number("promotion_amount"))}") }
                items += Card("totals", "Invoice totals", money(r.number("total_amount")),
                    "Gross: ${money(r.number("gross_amount"))}\nDiscounts: ${money(r.number("total_discount"))}\nItem promotions: ${money(r.number("total_item_promotion"))}\nInvoice promotion: ${money(r.number("promotion_amount"))}\nCashback: ${money(r.number("cashback_amount"))}\n${r.text("notes")}\n${r.text("void_reason")}")
                val location = r.obj("creation_location")
                if (!location.empty) items += Card("location", "Sale location", detail =
                    "${location.text("latitude")}, ${location.text("longitude")}\nAccuracy: ${location.text("accuracy_meters")} m",
                    actions = listOf(action("Open map", "map")))
                items += Card("audit", "Document history", detail = audit(r))
            }
            "cash" -> {
                val cash = s.options; val trip = s.extra; val f = trip.obj("financial_summary")
                items += Card("hold", "Cash in your custody", money(cash.number("cash_hold")),
                    "Available to return: ${money(cash.number("available_to_submit"))}\nPending: ${money(cash.number("pending_submissions"))}\n${trip.text("reference")} · ${trip.text("status")}",
                    if (trip.text("status") in listOf("operation", "ending") && cash.number("available_to_submit") > 0) listOf(action("Return cash", "submitCash")) else emptyList())
                items += Card("trip-cash", "Trip cash position", detail =
                    "Opening cash: ${money(trip.number("opening_cash_balance"))}\nCash sales: ${money(f.number("cash_hold_sales"))}\nCredit collected as cash: ${money(f.number("cash_credit_collected"))}\nConfirmed returned: ${money(f.number("cash_submitted_confirmed"))}\nPending handover: ${money(f.number("cash_submitted_pending"))}\nTrip responsibility: ${money(maxOf(0, trip.number("opening_cash_balance") + f.number("cash_hold_sales") + f.number("cash_credit_collected") - f.number("cash_submitted_confirmed")))}\nCash hold changes only after office confirmation.")
                s.data.rows("data").forEach { r ->
                    items += if (vm.tab == "ledger") Card("ledger${r.id}", r.text("reference"), money(r.number("amount_delta")),
                        r.text("type").replace('_', ' ') + "\n" + r.text("occurred_at") + "\n" + r.text("notes"))
                    else Card("cash${r.id}", r.text("reference"), money(r.number("amount")),
                        "${r.text("status").uppercase()} · ${r.obj("trip").text("reference")}\n${r.text("created_at")}\n${r.text("notes")}\n${r.text("cancel_reason")}",
                        if (r.text("status") == "pending") listOf(action("Cancel handover", "cancelCash", r.id)) else emptyList())
                }
            }
            "customers" -> {
                items += Card("new", "Customers", detail = "Search assigned regions and collect outstanding credit.",
                    actions = listOf(action("New customer", "customer_new")))
                s.data.rows("data").forEach { r ->
                    val canCollect = r.flag("is_active") && r.flag("credit_allowed") && r.number("outstanding_amount") > 0 &&
                        s.extra.text("status") == "operation" && s.extra.obj("region").id == r.obj("region").id
                    items += Card("customer${r.id}", "${r.text("code")} · ${r.name}", money(r.number("outstanding_amount")),
                        "Outstanding credit\n${if (r.flag("is_active")) "Active" else "Inactive"} · ${r.obj("region").name}\n${r.text("customer_type")} · ${r.text("phone")}\n${r.text("township")} · ${r.text("address")}\nCredit limit: ${money(r.number("credit_limit"))}\nAvailable credit: ${money(r.number("available_credit"))}\n${r.text("notes")}",
                        if (canCollect) listOf(action("Collect credit", "collect", r.id)) else emptyList())
                }
            }
        }
        if (vm.screen in listOf("stock", "sales", "cash", "customers")) {
            if (s.data.rows("data").isEmpty()) items += Card("empty", "No records", detail = "No records match this view.")
            val meta = s.data.obj("meta")
            val next = mutableListOf<CardAction>()
            if (vm.page > 1) next += action("Previous page", "previous")
            if (vm.page < meta.number("last_page")) next += action("Next page", "next")
            items += Card("pages", "Page ${vm.page} of ${maxOf(1, meta.number("last_page"))}", detail = "${meta.number("total")} records", actions = next)
        }
        cards.submitList(workspacePresentation(vm.screen, s, items, vm.tab))
        if (dialog == null && vm.saved.get<String>("dialog").orEmpty().isNotBlank()) {
            when (vm.saved.get<String>("dialog")) {
                "Record trip expense" -> expense(vm.saved["dialogId"] ?: 0L)
                "Return trip cash" -> submitCash()
                "Cancel cash handover" -> cancelCash(vm.saved["dialogId"] ?: 0L)
                "Collect customer credit" -> {
                    val id: Long = vm.saved["dialogId"] ?: 0L
                    if (s.data.rows("data").any { it.id == id }) collect(id)
                }
            }
        }
    }
    private fun audit(r: Record) = listOf("created", "dispatched", "received", "posted", "voided", "reversed", "cancelled")
        .mapNotNull { key -> r.text("${key}_at").takeIf { it.isNotBlank() }?.let { "${key.replaceFirstChar { it.uppercase() }}: $it · ${r.obj("${key}_by").name}" } }.joinToString("\n")
    private fun stockCard(r: Record): Card {
        val p = r.obj("product"); val unit = p.rows("units").find { it.flag("is_default_selling") }
        val factor = maxOf(1L, unit?.number("conversion_factor") ?: 1L)
        val baseName = p.text("base_unit", p.rows("units").find { it.flag("is_base") }?.name ?: "base units")
        val equivalent = if (factor > 1) "\n${r.number("quantity") / factor} ${unit?.name} + ${r.number("quantity") % factor} $baseName" else ""
        return Card("stock${r.id}", p.name, "${number(r.number("quantity"))} $baseName",
            "${p.text("sku")}$equivalent\nFOC: ${r.number("foc_quantity")} $baseName · Incoming: ${r.number("pending_quantity")} $baseName")
    }
    private fun saleCard(r: Record, commands: Boolean = false): Card {
        val actions = mutableListOf(action("View details", "sale_detail", r.id))
        if (commands && r.text("status") == "draft") {
            actions += action("Edit draft", "edit", r.id); actions += action("Post draft", "post", r.id); actions += action("Delete draft", "delete", r.id)
        }
        if (commands) actions += action("Print invoice", "print", r.id)
        return Card("sale${r.id}", r.obj("customer").name, money(r.number("total_amount")),
            "${r.text("reference")} · ${r.text("status").uppercase()}\n${r.text("payment_type")} · ${r.text("payment_method_name")}\n${r.text("created_at")}", actions)
    }
    private fun online(): Boolean {
        if (host.online) return true
        Snackbar.make(b.root, "Internet connection is required to complete this transaction.", Snackbar.LENGTH_LONG).show(); return false
    }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        if (!online() || vm.state.value.busy) return
        MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(message)
            .setNegativeButton("Cancel", null).setPositiveButton("Confirm") { _, _ -> action() }.show()
    }
    private fun act(a: CardAction) {
        when (a.key) {
            "new_sale" -> open(R.id.new_sale)
            "home" -> open(R.id.home)
            "stock" -> open(R.id.stock)
            "cash" -> open(R.id.cash)
            "sales" -> open(R.id.sales)
            "customers" -> open(R.id.customers)
            "customer_new" -> open(R.id.customer_new)
            "receiving" -> open(R.id.receiving, a.id)
            "sale_detail" -> open(R.id.sale_detail, a.id)
            "edit" -> open(R.id.new_sale, a.id)
            "next" -> { vm.page(vm.page + 1); b.list.scrollToPosition(0) }
            "previous" -> { vm.page(vm.page - 1); b.list.scrollToPosition(0) }
            "receive" -> confirm("Receive all stock?", "Confirm that every product, paid quantity and FOC quantity matches the physical delivery.") {
                vm.command("POST", "receivings/${a.id}/receive", notice = "Stock received.", destination = "home")
            }
            "post" -> {
                val sale = if (vm.screen == "sale_detail") record() else vm.state.value.data.rows("data").first { it.id == a.id }
                confirm("Post ${sale.text("reference")}?", "${money(sale.number("total_amount"))}\nStock and payment balances will update. Posted sales cannot be edited.") {
                    vm.command("POST", "sales/${a.id}/post", notice = "Sale posted.")
                }
            }
            "delete" -> confirm("Delete this draft?", "The draft will be permanently deleted. Posted sales cannot be deleted.") {
                vm.command("DELETE", "sales/${a.id}", notice = "Draft deleted.", destination = if (vm.screen == "sale_detail") "back" else "")
            }
            "end" -> confirm("Begin trip ending?", "This stops new sales. Return remaining stock and hand over cash; the office completes the trip.") {
                vm.command("POST", "trips/${a.id}/begin-ending", idempotent = false, notice = "Trip ending started.")
            }
            "expense" -> expense(a.id)
            "submitCash" -> submitCash()
            "cancelCash" -> cancelCash(a.id)
            "collect" -> collect(a.id)
            "print" -> printInvoice(a.id)
            "map" -> {
                val loc = record().obj("creation_location")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${loc.text("latitude")},${loc.text("longitude")}?q=${loc.text("latitude")},${loc.text("longitude")}"))
                runCatching { startActivity(intent) }.onFailure { Snackbar.make(b.root, requireContext().tr("No map application installed."), Snackbar.LENGTH_LONG).show() }
            }
        }
    }
    private fun formDialog(title: String, submit: String, build: (LinearLayout) -> (() -> Unit)) {
        if (!online()) return
        keepDialog = false
        vm.saved["dialog"] = title
        val form = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(context.dp(20), 0, context.dp(20), context.dp(16)) }
        dialogFields.clear(); dialogError = form.label("")
        val operation = build(form)
        dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(title).setView(ScrollView(requireContext()).apply { addView(form) })
            .setNegativeButton("Cancel", null).setPositiveButton(submit, null).create()
        dialog?.setOnShowListener { dialog?.getButton(-1)?.setOnClickListener { if (!vm.state.value.busy) operation() } }
        dialog?.setOnDismissListener {
            if (!keepDialog) {
                vm.saved["dialog"] = ""
                vm.saved.keys().filter { it.startsWith("dialog.field.") }.forEach { vm.saved.remove<String>(it) }
            }
        }
        dialog?.show()
        dialog?.getButton(-1)?.isEnabled = !vm.state.value.busy
        dialog?.getButton(-2)?.isEnabled = !vm.state.value.busy
        dialog?.setCancelable(!vm.state.value.busy)
    }
    private fun dialogField(form: LinearLayout, key: String, label: String, initial: String = "", type: Int = InputType.TYPE_CLASS_TEXT): com.google.android.material.textfield.TextInputEditText {
        val edit = form.field(label, vm.saved["dialog.field.$key"] ?: initial, type) { vm.saved["dialog.field.$key"] = it }
        dialogFields[key] = edit
        return edit
    }
    private fun expense(id: Long) = formDialog("Record trip expense", "Save expense") { form ->
        vm.saved["dialogId"] = id
        form.label("Expenses are recorded in the trip ledger; they do not reduce cash held.")
        val description = dialogField(form, "description", "Description"); val amount = dialogField(form, "amount", "Amount (MMK)", type = InputType.TYPE_CLASS_NUMBER)
        val notes = dialogField(form, "notes", "Notes")
        dialogFields.putAll(mapOf("description" to description, "amount" to amount, "notes" to notes))
        val submit: () -> Unit = {
            if (description.text.isNullOrBlank() || amount.text.toString().toLongOrNull()?.let { it > 0 } != true) dialogError?.text = requireContext().tr("Enter a description and a positive whole amount.")
            else vm.command("POST", "trips/$id/expenses", mapOf("description" to description.text.toString(), "amount" to amount.text.toString().toLong(), "notes" to notes.text.toString()), false, "Expense recorded. Cash held is unchanged.")
        }; submit
    }
    private fun submitCash() = formDialog("Return trip cash", "Submit for confirmation") { form ->
        val available = vm.state.value.options.number("available_to_submit")
        form.label("Available: ${money(available)}\nLinked to the current trip. The office must confirm the handover.")
        val amount = dialogField(form, "amount", "Amount (MMK)", available.toString(), InputType.TYPE_CLASS_NUMBER)
        val notes = dialogField(form, "notes", "Handover note")
        dialogFields.putAll(mapOf("amount" to amount, "notes" to notes))
        val submit: () -> Unit = {
            val value = amount.text.toString().toLongOrNull()
            if (value == null || value <= 0 || value > available) dialogError?.text = requireContext().tr("Enter an amount from 1 to $available.")
            else vm.command("POST", "cash-submissions", mapOf("amount" to value, "notes" to notes.text.toString()), notice = "Cash handover awaits office confirmation.")
        }; submit
    }
    private fun cancelCash(id: Long) = formDialog("Cancel cash handover", "Cancel handover") { form ->
        vm.saved["dialogId"] = id
        val reason = dialogField(form, "reason", "Cancellation reason")
        dialogFields["reason"] = reason
        val submit: () -> Unit = {
            if (reason.text.isNullOrBlank()) dialogError?.text = requireContext().tr("Enter a reason.")
            else vm.command("POST", "cash-submissions/$id/cancel", mapOf("reason" to reason.text.toString()), notice = "Handover cancelled. Cash hold is unchanged.")
        }; submit
    }
    private fun collect(id: Long) = formDialog("Collect customer credit", "Record collection") { form ->
        vm.saved["dialogId"] = id
        val customer = vm.state.value.data.rows("data").first { it.id == id }
        val methods = vm.state.value.options.rows("payment_methods")
        var method: String = vm.saved["dialog.field.method"] ?: methods.firstOrNull()?.text("key").orEmpty()
        form.label(customer.name + "\nOutstanding: " + money(customer.number("outstanding_amount")))
        val amount = dialogField(form, "amount", "Amount (MMK)", customer.number("outstanding_amount").toString(), InputType.TYPE_CLASS_NUMBER)
        val presets = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        form.addView(presets)
        listOf("25%" to 4, "50%" to 2, "Full" to 1).forEach { (label, divisor) ->
            presets.button(label) { amount.setText(maxOf(1L, customer.decimal("outstanding_amount")
                .divide(java.math.BigDecimal(divisor), 0, java.math.RoundingMode.HALF_UP).toLong()).toString()) }
                .layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val preview = form.label("")
        fun updatePreview() {
            val value = amount.text.toString().toLongOrNull() ?: 0
            preview.text = requireContext().tr("Remaining credit") + ": " + money(maxOf(0L, customer.number("outstanding_amount") - value)) + "\n" +
                requireContext().tr(if (methods.find { it.text("key") == method }?.flag("adds_to_cash_hold") == true)
                    "Added to your current trip cash hold automatically." else "Recorded in this trip without increasing your cash hold.")
        }
        amount.doAfterTextChanged { updatePreview() }
        form.choice("Payment method", methods.map { it.text("key") to it.name }, method) { method = it; vm.saved["dialog.field.method"] = it; updatePreview() }
        updatePreview()
        val notes = dialogField(form, "notes", "Notes")
        dialogFields.putAll(mapOf("amount" to amount, "notes" to notes))
        val submit: () -> Unit = {
            val value = amount.text.toString().toLongOrNull()
            if (value == null || value <= 0 || value > customer.number("outstanding_amount") || method.isBlank()) dialogError?.text = requireContext().tr("Check the amount and payment method.")
            else vm.command("POST", "credit-collections", mapOf("customer_id" to id, "amount" to value, "payment_method" to method, "notes" to notes.text.toString()), notice = "Credit collection recorded.")
        }; submit
    }
    private fun filters() {
        val form = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(context.dp(20), 0, context.dp(20), 0) }
        val query = vm.query.toMutableMap()
        form.field("Search", query["search"].orEmpty()) { query["search"] = it }
        if (vm.screen == "sales") {
            var tripsJob: kotlinx.coroutines.Job? = null
            val tripHost = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            fun loadTrips() {
                tripsJob?.cancel()
                tripHost.removeAllViews()
                val ready = query["period"] == "today" || (query["period"] == "range" && !query["date_from"].isNullOrBlank() && !query["date_to"].isNullOrBlank())
                if (!ready) { tripHost.label("Choose a duration first to load trips."); return }
                tripHost.label("Loading trips…")
                tripsJob = viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val options = host.repository.get("sale-history-options", query.filterKeys { it in setOf("period", "date_from", "date_to") }.filterValues { it.isNotBlank() })
                        tripHost.removeAllViews()
                        tripHost.choice("Trip", listOf("" to "All trips in this duration") + options.rows("trips").map { it.id.toString() to (it.text("reference") + " · " + it.name) }, query["trip_id"].orEmpty()) { query["trip_id"] = it }
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { tripHost.removeAllViews(); tripHost.label(e.message.orEmpty()) }
                }
            }
            form.choice("Status", listOf("" to "All", "draft" to "Draft", "posted" to "Posted", "voided" to "Voided"), query["status"].orEmpty()) { query["status"] = it }
            form.choice("Payment", listOf("" to "All", "cash" to "Cash / banking", "credit" to "Credit"), query["payment_type"].orEmpty()) { query["payment_type"] = it }
            form.choice("Duration", listOf("" to "All time", "today" to "Today", "range" to "Date range"), query["period"].orEmpty()) {
                query["period"] = it; query.remove("trip_id")
                if (it != "range") { query.remove("date_from"); query.remove("date_to") }
                loadTrips()
            }
            fun date(key: String, title: String) {
                val edit = form.field(title, query[key].orEmpty())
                edit.isFocusable = false
                edit.setOnClickListener {
                    val now = java.util.Calendar.getInstance()
                    android.app.DatePickerDialog(requireContext(), { _, year, month, day ->
                        val value = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                        query[key] = value; query["period"] = "range"; query.remove("trip_id"); edit.setText(value); loadTrips()
                    }, now.get(1), now.get(2), now.get(5)).show()
                }
            }
            date("date_from", "From date"); date("date_to", "To date")
            form.addView(tripHost)
            loadTrips()
        }
        MaterialAlertDialogBuilder(requireContext()).setTitle("Filters").setView(ScrollView(requireContext()).apply { addView(form) })
            .setNeutralButton("Clear") { _, _ -> vm.saved["searchDraft"] = ""; vm.filter(emptyMap()) }
            .setNegativeButton("Cancel", null).setPositiveButton("Apply") { _, _ -> vm.saved["searchDraft"] = query["search"].orEmpty(); vm.filter(query.filterValues { it.isNotBlank() }) }.show()
    }
    private fun printInvoice(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val sale = if (vm.screen == "sale_detail") record() else host.repository.get("sales/$id").obj("data")
                InvoicePrinter.print(requireActivity(), sale, host.repository.branding, host.repository.user.value?.id ?: 0) {
                    com.example.superkingsale.printing.ThermalPrintDialog.forSale(id, host.repository.user.value?.id ?: 0).show(parentFragmentManager, "thermal")
                }
            } catch (e: Exception) { binding?.let { Snackbar.make(it.root, e.message.orEmpty(), Snackbar.LENGTH_LONG).show() } }
        }
    }
    private fun renderForm(s: WorkspaceState) {
        b.form.removeAllViews()
        if (vm.screen == "customer_new") {
            b.form.eyebrow("ROUTE CUSTOMERS")
            b.form.heading("New customer", "New customers are cash-only. The office manages credit eligibility.")
            val panel = b.form.panel("Customer information", "CASH-ONLY PROFILE")
            val fieldsGrid = panel.grid()
            val values = mutableMapOf<String, Any?>()
            fun field(key: String, title: String, default: String = "", type: Int = InputType.TYPE_CLASS_TEXT) {
                val value: String = vm.saved["form.$key"] ?: default; values[key] = value
                (if (key == "notes") panel else fieldsGrid.cell()).field(title, value, type) { values[key] = it; vm.saved["form.$key"] = it }
            }
            field("name", "Customer name"); field("customer_type", "Customer type", "Shop")
            field("phone", "Phone", type = InputType.TYPE_CLASS_PHONE)
            val regions = s.data.rows("regions")
            var region: String = vm.saved["form.region"] ?: regions.firstOrNull()?.id?.toString().orEmpty()
            fieldsGrid.cell().choice("Region", regions.map { it.id.toString() to it.name }, region) { region = it; vm.saved["form.region"] = it }
            field("township", "Township"); field("address", "Address"); field("notes", "Notes", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            val actions = panel.grid(160, 2)
            actions.cell().button("Cancel") { findNavController().popBackStack() }.outlined()
            actions.cell().button("Create customer") {
                if (!online()) return@button
                if (values["name"].toString().isBlank() || region.toLongOrNull() == null) { b.status.text = requireContext().tr("Enter a name and region."); b.status.isVisible = true }
                else vm.command("POST", "customers", values + ("region_id" to region.toLong()), false, "Customer created.", "customer")
            }
        } else {
            val r = s.data.obj("representative"); val account = r.obj("account")
            b.form.eyebrow("ACCOUNT SETTINGS")
            b.form.heading("Profile & security", "${r.name} · ${r.text("code")} · ${r.obj("primary_warehouse").name}")
            val columns = b.form.grid(350, 2)
            val personal = columns.cell().panel("Personal profile", "REPRESENTATIVE ACCOUNT")
            val security = columns.cell().panel("Password & security", "ACCOUNT ACCESS")
            security.copyText("Keep your account protected with a private password.", 13f, color = R.color.workspace_muted)
            val fields = mutableMapOf<String, com.google.android.material.textfield.TextInputEditText>()
            personal.label("Assigned regions: " + r.rows("regions").joinToString { it.name })
            val personalFields = personal.grid()
            for ((key, title) in listOf("name" to "Name", "username" to "Username", "email" to "Email", "phone" to "Phone")) {
                val initial = if (key in listOf("name", "username", "email")) account.text(key, r.text(key)) else r.text(key)
                fields[key] = personalFields.cell().field(title, vm.saved["profile.$key"] ?: initial) { vm.saved["profile.$key"] = it }
            }
            personal.button("Save profile") { if (online()) vm.command("PUT", "profile", fields.mapValues { it.value.text.toString() }, false, "Profile updated.") }
            security.button("Change password") {
                formDialog("Change password", "Update password") { form ->
                    val passwordType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    val current = form.field("Current password", type = passwordType)
                    val password = form.field("New password", type = passwordType)
                    val confirm = form.field("Confirm password", type = passwordType)
                    dialogFields.putAll(mapOf("current_password" to current, "password" to password, "password_confirmation" to confirm))
                    val submit: () -> Unit = {
                        if (password.text.toString() != confirm.text.toString()) dialogError?.text = requireContext().tr("Passwords do not match.")
                        else vm.command("PUT", "profile/password", mapOf("current_password" to current.text.toString(), "password" to password.text.toString(), "password_confirmation" to confirm.text.toString()), false, "Password updated.")
                    }; submit
                }
            }
            val display = b.form.panel("Display preferences", "YOUR WORKSPACE")
            val displayGrid = display.grid()
            val devices = b.form.panel("Display & printing", "DEVICE SETTINGS")
            devices.button("Thermal printer / PDF") { com.example.superkingsale.printing.ThermalPrintDialog.forSale(userId = host.repository.user.value?.id ?: 0).show(parentFragmentManager, "thermal") }
            devices.button("Test device GPS") {
                locationCapture.start { fix ->
                    MaterialAlertDialogBuilder(requireContext()).setTitle("GPS diagnostic")
                        .setMessage(locationCapture.describe(fix) + "\n\n" + requireContext().tr("Diagnostic only. No sale or server record was created."))
                        .setPositiveButton("OK", null).show()
                }
            }
            val prefs = requireContext().getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE)
            displayGrid.cell().choice("Theme", listOf("-1" to "System", "1" to "Light", "2" to "Dark"), prefs.getInt("theme", -1).toString()) {
                prefs.edit().putInt("theme", it.toInt()).apply(); androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(it.toInt())
            }
            displayGrid.cell().choice("Text size", listOf("1.0" to "System default", "1.15" to "Large", "1.3" to "Extra large"), prefs.getFloat("fontScale", 1f).toString()) {
                prefs.edit().putFloat("fontScale", it.toFloat()).apply(); requireActivity().recreate()
            }
            displayGrid.cell().choice("Density", listOf("comfortable" to "Comfortable", "compact" to "Compact"), prefs.getString("density", "comfortable").orEmpty()) {
                prefs.edit().putString("density", it).apply(); requireActivity().recreate()
            }
            displayGrid.cell().choice("Language", listOf("en" to "English", "my" to "မြန်မာ"), prefs.getString("language", "en").orEmpty()) {
                prefs.edit().putString("language", it).apply(); requireActivity().recreate()
            }
            val paperKey = "paper.${host.repository.user.value?.id}"
            devices.choice("Invoice paper", listOf("a4" to "A4", "a5" to "A5", "80mm" to "80 mm", "58mm" to "58 mm", "50mm" to "50 mm"), prefs.getString(paperKey, "a4").orEmpty()) { prefs.edit().putString(paperKey, it).apply() }
            devices.label("Printing uses Android's print service. Choose a printer or save the invoice as PDF.")
            devices.label("Thermal sizes require a compatible print service. Check the paper size in Android's preview; unsupported sizes may fall back to Letter or A4.")
            b.form.removeView(display)
            b.form.addView(display, b.form.indexOfChild(columns))
        }
    }
}

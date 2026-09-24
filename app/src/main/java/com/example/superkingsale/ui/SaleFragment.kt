package com.example.superkingsale.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.superkingsale.*
import com.example.superkingsale.data.Record
import com.example.superkingsale.databinding.FragmentWorkspaceBinding
import com.example.superkingsale.sale.*
import com.example.superkingsale.ui.LocalizedDialogBuilder as MaterialAlertDialogBuilder
import kotlinx.coroutines.*

class SaleFragment : Fragment(R.layout.fragment_workspace) {
    private val host get() = requireActivity() as MainActivity
    private val vm: SaleViewModel by viewModels {
        viewModelFactory { initializer { SaleViewModel(host.repository, createSavedStateHandle()) } }
    }
    private var binding: FragmentWorkspaceBinding? = null
    private val b get() = checkNotNull(binding)
    private var version = -1
    private var renderedStep = 0
    private var confirmation: androidx.appcompat.app.AlertDialog? = null
    private val locationCapture = com.example.superkingsale.location.ForegroundLocationCapture(this)
    private var productQuery = ""
    private val productAdapter = CardAdapter { a ->
        vm.state.value.options.rows("products").find { it.id == a.id }?.let(vm::toggle)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentWorkspaceBinding.bind(view); version = -1
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(b.controls) { _, insets ->
            val controls = binding?.controls ?: return@setOnApplyWindowInsetsListener insets
            val typing = insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
            if (vm.state.value.draft.step == 2) {
                for (i in 0 until minOf(3, controls.childCount)) controls.getChildAt(i).isVisible = !typing
            } else controls.isVisible = !typing
            insets
        }
        b.configurePullRefresh({
            val s = vm.state.value
            !s.loading && !s.busy && s.confirmSale == null && s.draft.pendingCreateJson == null
        }) { vm.load() }
        b.list.layoutManager = LinearLayoutManager(requireContext()); b.list.adapter = productAdapter
        b.retry.setOnClickListener { vm.load() }
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Long>("createdCustomer")
            ?.observe(viewLifecycleOwner) { id ->
                if (id > 0) { vm.createdCustomer(id); findNavController().currentBackStackEntry?.savedStateHandle?.set("createdCustomer", 0L) }
            }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    b.progress.isVisible = state.busy
                    b.swipeRefresh.isEnabled = !state.busy && state.draft.pendingCreateJson == null
                    b.swipeRefresh.isRefreshing = state.loading
                    b.status.text = requireContext().tr(state.error + if (state.fields.isNotEmpty()) "\n" + state.fields.entries.joinToString("\n") { "${it.key}: ${it.value}" } else "")
                    b.status.isVisible = b.status.text.isNotBlank()
                    b.retry.isVisible = state.error.isNotBlank() && !state.busy && state.draft.pendingCreateJson == null
                    if (version != state.version && !state.options.empty) { version = state.version; render(state) }
                    b.form.enableChildren(!state.busy && !state.loading)
                    b.controls.enableChildren(!state.busy && !state.loading)
                    b.retry.isEnabled = !state.loading && !state.busy
                    productAdapter.busy = state.busy || state.loading
                    if (state.confirmSale != null && confirmation == null) {
                        val sale = state.confirmSale
                        confirmation = MaterialAlertDialogBuilder(requireContext()).setTitle("Post ${sale.text("reference")}?")
                            .setMessage("Server total: ${money(sale.number("total_amount"))}\n${sale.obj("customer").name}\n${sale.text("payment_type")} · ${sale.text("payment_method_name")}\n\nStock and payment balances will update immediately. Posted sales cannot be edited.")
                            .setNegativeButton("Keep draft") { _, _ -> vm.dismissConfirm() }
                            .setPositiveButton("Post sale") { _, _ -> if (host.online) vm.post() else vm.error("Connect to the internet before posting.") }
                            .setOnCancelListener { vm.dismissConfirm() }.create()
                        confirmation?.setOnDismissListener { confirmation = null }
                        confirmation?.show()
                    }
                    state.completed?.let { id ->
                        vm.reset()
                        host.open(R.id.sale_detail, id)
                    }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); vm.refreshIfChanged() }
    override fun onDestroyView() {
        locationCapture.cancel(); confirmation?.dismiss(); confirmation = null; binding = null; super.onDestroyView()
    }
    private fun render(s: SaleState) {
        val d = s.draft
        val stepChanged = renderedStep != d.step
        renderedStep = d.step
        b.controls.removeAllViews(); b.form.removeAllViews()
        b.formScroll.isVisible = d.step != 2; b.list.isVisible = d.step == 2
        b.controls.eyebrow("SALES WORKSPACE")
        b.controls.heading("New sale")
        b.controls.stepper(d.step)
        b.controls.copyText("Step ${d.step} of 4 · " + listOf("Information", "Products", "Quantity", "Review & submit")[d.step - 1], 12f, color = R.color.workspace_muted)
        if (d.pendingCreateJson != null) {
            b.formScroll.isVisible = true; b.list.isVisible = false
            b.form.heading("Confirm interrupted save", "The original request is retained. Retry it to recover the same draft without creating a duplicate.")
            b.form.button("Retry original save") { if (host.online) vm.save(false) }
            b.form.button("View sales history") { host.open(R.id.sales) }
            return
        }
        if (d.reference.isNotBlank()) b.controls.label("Draft ${d.reference}")
        if (s.notice.isNotBlank()) b.controls.label(s.notice)
        when (d.step) {
            1 -> {
                val information = b.form.panel("Sale information", "CREATE CUSTOMER SALE")
                information.copyText("${s.options.obj("trip").text("reference")} · ${s.options.obj("trip").obj("region").name}", 12f, color = R.color.workspace_muted)
                val columns = information.grid(340, 2)
                val customerPanel = columns.cell()
                val paymentPanel = columns.cell()
                val customer = vm.customer()
                customerPanel.copyText("Customer", 13f, true)
                customerPanel.button(if (customer.empty) "Choose customer" else "${customer.text("code")} · ${customer.name}") { chooseCustomer() }.outlined()
                customerPanel.button("Add new customer") { host.open(R.id.customer_new) }.outlined()
                if (!customer.empty) customerPanel.copyText("Credit allowed: ${if (customer.flag("credit_allowed")) "Yes" else "No"}\nOutstanding: ${money(customer.number("outstanding_amount"))}\nAvailable credit: ${money(customer.number("available_credit"))}", 12f, color = R.color.workspace_muted)
                paymentPanel.options("Payment type", listOf("cash" to "Cash / banking") + if (customer.flag("credit_allowed")) listOf("credit" to "Credit") else emptyList(), d.paymentType) { v -> vm.change(true) { it.copy(paymentType = v) } }
                if (d.paymentType == "cash") {
                    val methods = s.options.rows("payment_methods")
                    paymentPanel.options("Payment method", methods.map { it.text("key") to it.name }, d.paymentMethod) { v -> vm.change(true) { it.copy(paymentMethod = v) } }
                    val method = methods.find { it.text("key") == d.paymentMethod }
                    paymentPanel.copyText(if (method?.flag("adds_to_cash_hold") == true) "This payment increases cash held." else "This payment goes directly to banking and does not increase cash held.", 12f, color = R.color.workspace_muted)
                }
                information.field("Notes", d.notes, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE) { v -> vm.change { it.copy(notes = v) } }
                if (d.id == 0L) {
                    val location = b.form.panel("Location information")
                    location.copyText(if (d.latitude == null) "Current location is required when creating a sale." else "Location ready · accuracy about ${d.accuracy?.toInt()} m", 13f, color = R.color.workspace_muted)
                    location.button("Capture current location") { captureLocation() }.outlined()
                    location.button("Location settings") {
                        startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + requireContext().packageName)))
                    }
                }
            }
            2 -> {
                b.controls.field("Find product by name or SKU", productQuery) { productQuery = it; products() }
                products()
            }
            3 -> {
                b.form.heading("Quantities & offers", "Paid and FOC stock are validated separately in base units.")
                d.lines.forEach { line ->
                    val product = vm.product(line)
                    val panel = b.form.panel(product.name)
                    panel.copyText("${product.text("sku")} · Available: ${product.number("quantity")} paid + ${product.number("foc_quantity")} FOC base units", 12f, color = R.color.workspace_muted)
                    val fields = panel.grid()
                    val units = product.rows("units").map { it.id.toString() to ("${it.name} · ${it.number("conversion_factor")} base units") }
                    fields.cell().choice("Selling unit", units, line.unitId.toString()) { value -> vm.line(line.productId) { it.copy(unitId = value.toLong()) } }
                    fields.cell().field("Quantity", line.quantity, InputType.TYPE_CLASS_NUMBER) { value -> vm.line(line.productId) { it.copy(quantity = value) } }
                    fields.cell().choice("FOC unit", units, (if (line.focUnitId > 0) line.focUnitId else line.unitId).toString()) { value -> vm.line(line.productId) { it.copy(focUnitId = value.toLong()) } }
                    fields.cell().field("FOC quantity", line.focQuantity, InputType.TYPE_CLASS_NUMBER) { value -> vm.line(line.productId) { it.copy(focQuantity = value) } }
                    fields.cell().field("Discount (%)", line.discount, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) { value -> vm.line(line.productId) { it.copy(discount = value) } }
                    fields.cell().field("Promotion title", line.promotionTitle) { value -> vm.line(line.productId) { it.copy(promotionTitle = value) } }
                    fields.cell().field("Promotion amount (MMK)", line.promotion, InputType.TYPE_CLASS_NUMBER) { value -> vm.line(line.productId) { it.copy(promotion = value) } }
                    panel.button("Remove ${product.name}") { vm.toggle(product) }.outlined()
                }
            }
            4 -> {
                b.form.heading("Review & submit", "${vm.customer().name}\n${d.paymentType.uppercase()} · ${d.paymentMethod}")
                d.lines.forEach { line ->
                    b.form.label("${vm.product(line).name}\n${line.quantity} ${vm.unit(line).name} × ${money(vm.price(line) ?: 0)}\nFOC: ${line.focQuantity} ${vm.unit(line, true).name}\nDiscount: ${line.discount}% · ${line.promotionTitle} ${money(line.promotion.toLongOrNull() ?: 0)}\nLine: ${money(vm.total(line))}")
                }
                val total = b.form.label("Estimated total: " + money(vm.payable()))
                b.form.field("Cashback (MMK)", d.cashback, InputType.TYPE_CLASS_NUMBER) { value ->
                    vm.change { it.copy(cashback = value) }; total.text = requireContext().tr("Estimated total: " + money(vm.payable()))
                }
                if (d.invoicePromotion > 0) b.form.label("Existing invoice promotion: ${d.invoicePromotionTitle} · ${money(d.invoicePromotion)}")
                b.form.label("The server calculates the authoritative total. Save first, then confirm that amount before posting.")
                if (d.notes.isNotBlank()) b.form.label(d.notes)
                b.form.button("Save draft") { save(false) }
                b.form.button("Save & review server total") { save(true) }
                if (d.id > 0) b.form.button("Open saved draft") { host.open(R.id.sale_detail, d.id) }
                if (d.id == 0L) b.form.button("Refresh location") { captureLocation() }
            }
        }
        val controls = if (d.step == 2) b.controls else b.form
        val actions = controls.grid(if (d.step == 2) 90 else 150, if (d.step == 2) 3 else 2)
        if (d.step > 1) actions.cell().button("Back") { vm.back() }.outlined()
        if (d.step < 4) actions.cell().button("Continue") { vm.next() }
        actions.cell().button("Start another sale") {
            MaterialAlertDialogBuilder(requireContext()).setTitle("Start another sale?")
                .setMessage(if (d.id > 0) "The saved draft remains in Sales history." else "The unsaved local draft will be discarded.")
                .setNegativeButton("Cancel", null).setPositiveButton("Start new") { _, _ -> vm.reset() }.show()
        }.outlined()
        if (stepChanged) {
            b.formScroll.post { binding?.formScroll?.scrollTo(0, 0) }
            b.list.scrollToPosition(0)
        }
        androidx.core.view.ViewCompat.requestApplyInsets(b.root)
    }
    private fun products() {
        val state = vm.state.value
        val filtered = state.options.rows("products").filter { (it.name + it.text("sku")).contains(productQuery, true) }
        productAdapter.submitList(filtered.map { p ->
            val selected = state.draft.lines.any { it.productId == p.id }
            Card("product${p.id}", p.name, "${p.number("quantity")} paid · ${p.number("foc_quantity")} FOC",
                p.text("sku"), listOf(CardAction(if (selected) "✓ Selected · Remove" else "Add product", "toggle", p.id)), kind = CardKind.ROW)
        })
    }
    private fun chooseCustomer() {
        val form = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(context.dp(16), 0, context.dp(16), 0) }
        val recycler = androidx.recyclerview.widget.RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context); layoutParams = LinearLayout.LayoutParams(-1, context.dp(360))
        }
        var dialog: androidx.appcompat.app.AlertDialog? = null
        val adapter = CardAdapter { action ->
            vm.change(true) { it.copy(customerId = action.id, paymentType = "cash") }; dialog?.dismiss()
        }
        fun filter(query: String) {
            adapter.submitList(vm.state.value.options.rows("customers").filter { (it.name + it.text("code")).contains(query, true) }
                .map { Card("customer${it.id}", it.name, detail = it.text("code") + " · " + it.obj("region").name,
                    actions = listOf(CardAction("Select", "select", it.id)), kind = CardKind.ROW) })
        }
        form.field("Search customers") { filter(it) }
        form.addView(recycler); recycler.adapter = adapter; filter("")
        dialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Choose customer").setView(form).setNegativeButton("Cancel", null).create()
        dialog.show()
    }
    private fun save(post: Boolean) {
        if (!host.online) { vm.error("Internet connection is required to complete this transaction."); return }
        if (vm.state.value.draft.id == 0L && vm.state.value.draft.pendingCreateJson == null) captureLocation { vm.save(post) }
        else vm.save(post)
    }
    private fun captureLocation(after: (() -> Unit)? = null) {
        locationCapture.start { location ->
            vm.location(location.latitude, location.longitude, location.accuracy)
            after?.invoke()
        }
    }
}

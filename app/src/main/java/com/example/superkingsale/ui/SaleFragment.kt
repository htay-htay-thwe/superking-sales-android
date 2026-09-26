package com.example.superkingsale.ui

import android.Manifest
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.ImageView
import android.widget.TextView
import android.view.Gravity
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
    private var customerSheet: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private var customerPopup: android.widget.PopupWindow? = null
    private val locationCapture = com.example.superkingsale.location.ForegroundLocationCapture(this)
    private var productQuery = ""
    private var customerMenuOpen = false
    private var customerQuery = ""
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
        locationCapture.cancel(); confirmation?.dismiss(); confirmation = null; customerPopup?.dismiss(); customerPopup = null; customerSheet?.dismiss(); customerSheet = null; binding = null; super.onDestroyView()
    }
    private fun render(s: SaleState) {
        val d = s.draft
        val stepChanged = renderedStep != d.step
        renderedStep = d.step
        b.topBar.removeAllViews(); b.controls.removeAllViews(); b.form.removeAllViews()
        b.formScroll.isVisible = d.step != 2; b.list.isVisible = d.step == 2
        b.stepActions.isVisible = d.step in 2..4
        b.stepActions.removeAllViews(); b.stepActions.orientation = LinearLayout.HORIZONTAL
        b.contentCard.background = if (d.step in 2..4) GradientDrawable().apply {
            cornerRadius = requireContext().dp(8).toFloat()
            setColor(requireContext().ink(R.color.workspace_surface))
            setStroke(requireContext().dp(1), requireContext().ink(R.color.workspace_line))
        } else null
        b.contentCard.setPadding(0, if (d.step in 2..4) requireContext().dp(8) else 0, 0, 0)
        (b.contentCard.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            val inset = if (d.step in 2..4) requireContext().dp(8) else 0
            params.setMargins(inset, if (d.step in 2..4) requireContext().dp(4) else 0, inset, if (d.step in 2..4) requireContext().dp(4) else 0)
            b.contentCard.layoutParams = params
        }
        b.topBar.copyText("Customer sales", 12f, color = R.color.workspace_muted)
        val pageTitle = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        pageTitle.addView(TextView(requireContext()).apply {
            text = requireContext().tr("New sale"); textSize = 20f; setTextColor(context.ink(R.color.workspace_text))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        pageTitle.addView(TextView(requireContext()).apply {
            text = "• " + requireContext().tr("Server priced"); textSize = 11f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(context.ink(R.color.workspace_info)); setPadding(context.dp(9), context.dp(5), context.dp(9), context.dp(5))
            background = GradientDrawable().apply { cornerRadius = context.dp(16).toFloat(); setColor(context.ink(R.color.workspace_info_tint)) }
        })
        b.topBar.addView(pageTitle, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = if (d.step > 1) requireContext().dp(8) else 0
        })
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
                renderInformation(s)
            }
            2 -> {
                products()
            }
            3 -> {
                b.form.copyText("Quantity", 18f, true)
                b.form.copyText("Set the required quantity for every selected product.", 12f, color = R.color.workspace_muted)
                b.form.addView(View(requireContext()).apply {
                    setBackgroundColor(context.ink(R.color.workspace_line))
                }, LinearLayout.LayoutParams(-1, requireContext().dp(1)).apply {
                    topMargin = requireContext().dp(8); bottomMargin = requireContext().dp(10)
                })
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
                b.form.copyText("Review & submit", 18f, true)
                b.form.copyText("Confirm the sale details before saving or posting.", 12f, color = R.color.workspace_muted)
                b.form.addView(View(requireContext()).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, requireContext().dp(1)).apply {
                    topMargin = requireContext().dp(8); bottomMargin = requireContext().dp(10)
                })
                val summary = b.form.panel("").apply { setPadding(requireContext().dp(12), requireContext().dp(10), requireContext().dp(12), requireContext().dp(10)) }
                summary.copyText("Customer", 11f, color = R.color.workspace_muted)
                summary.copyText(vm.customer().name, 14f, true)
                summary.copyText("Payment", 11f, color = R.color.workspace_muted).apply { setPadding(0, requireContext().dp(8), 0, 0) }
                summary.copyText("${d.paymentType.replaceFirstChar { it.uppercase() }} · ${d.paymentMethod}", 14f, true)
                if (d.notes.isNotBlank()) {
                    summary.copyText("Notes", 11f, color = R.color.workspace_muted).apply { setPadding(0, requireContext().dp(8), 0, 0) }
                    summary.copyText(d.notes, 13f)
                }
                d.lines.forEach { line ->
                    val product = vm.product(line)
                    val item = b.form.panel(product.name)
                    item.copyText(product.text("sku"), 11f, color = R.color.workspace_muted)
                    item.copyText("${line.quantity} ${vm.unit(line).name} × ${money(vm.price(line) ?: 0)}", 13f, true)
                    item.copyText("FOC: ${line.focQuantity} ${vm.unit(line, true).name} · Discount: ${line.discount}%", 11f, color = R.color.workspace_muted)
                    item.copyText("Line total: ${money(vm.total(line))}", 13f, true).apply { gravity = Gravity.END }
                }
                val total = b.form.label("Estimated total: " + money(vm.payable()))
                b.form.field("Cashback (MMK)", d.cashback, InputType.TYPE_CLASS_NUMBER) { value ->
                    vm.change { it.copy(cashback = value) }; total.text = requireContext().tr("Estimated total: " + money(vm.payable()))
                }
                if (d.invoicePromotion > 0) b.form.label("Existing invoice promotion: ${d.invoicePromotionTitle} · ${money(d.invoicePromotion)}")
                b.form.label("The server calculates the authoritative total. Save first, then confirm that amount before posting.")
                if (d.notes.isNotBlank()) b.form.label(d.notes)
                if (d.id > 0) b.form.button("Open saved draft") { host.open(R.id.sale_detail, d.id) }
            }
        }
        if (d.step == 1) {
            if (stepChanged) b.formScroll.post { binding?.formScroll?.scrollTo(0, 0) }
            androidx.core.view.ViewCompat.requestApplyInsets(b.root)
            return
        }
        if (d.step > 1) {
            b.controls.stepper(d.step, requireContext().dp(16))
            if (d.step == 2) {
                val productHeader = requireContext().column().apply {
                    setPadding(0, requireContext().dp(8), 0, 0)
                }
                b.controls.addView(productHeader, LinearLayout.LayoutParams(-1, -2))
                productHeader.copyText("Products", 18f, true)
                productHeader.copyText("Choose one or more products for this sale.", 12f, color = R.color.workspace_muted)
                productHeader.addView(View(requireContext()).apply {
                    setBackgroundColor(context.ink(R.color.workspace_line))
                }, LinearLayout.LayoutParams(-1, requireContext().dp(1)).apply {
                    topMargin = requireContext().dp(8); bottomMargin = requireContext().dp(2)
                })
                productHeader.field("Search by product name or SKU", productQuery) { productQuery = it; products() }
            }
        }
        if (d.step in 2..3) {
            val actions = b.stepActions.grid(90, 2)
            actions.cell().button("Back") { vm.back() }.outlined()
            actions.cell().button(if (d.step == 2) "Continue to quantity" else "Continue to review") { vm.next() }.apply {
                backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
                setTextColor(context.ink(R.color.workspace_surface))
            }
        } else if (d.step == 4) {
            b.stepActions.orientation = LinearLayout.VERTICAL
            val footer = requireContext().column()
            b.stepActions.addView(footer, LinearLayout.LayoutParams(-1, -2))
            val actions = footer.grid(140, 2, gapDp = 4)
            actions.cell().button("Back") { vm.back() }.outlined()
            actions.cell().button("Save draft") { save(false) }.outlined()
            footer.button("Save & review") { save(true) }.apply {
                backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
                setTextColor(context.ink(R.color.workspace_surface))
            }
        } else {
            val actions = b.form.grid(150, 2)
            if (d.step > 1) actions.cell().button("Back") { vm.back() }.outlined()
            if (d.step < 4) actions.cell().button("Continue") { vm.next() }
            if (d.step != 3) actions.cell().button("Start another sale") {
                MaterialAlertDialogBuilder(requireContext()).setTitle("Start another sale?")
                    .setMessage(if (d.id > 0) "The saved draft remains in Sales history." else "The unsaved local draft will be discarded.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Start new") { _, _ -> vm.reset() }.show()
            }.outlined()
        }
        if (stepChanged) {
            b.formScroll.post { binding?.formScroll?.scrollTo(0, 0) }
            b.list.scrollToPosition(0)
        }
        androidx.core.view.ViewCompat.requestApplyInsets(b.root)
    }
    private fun renderInformation(s: SaleState) {
        val ui = requireContext()
        val d = s.draft
        val customer = vm.customer()
        val card = b.form.panel("").apply {
            setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14))
            background = GradientDrawable().apply {
                cornerRadius = ui.dp(8).toFloat()
                setColor(context.ink(R.color.workspace_surface))
                setStroke(ui.dp(1), context.ink(R.color.workspace_line))
            }
        }
        val cardHeading = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val headingCopy = requireContext().column().apply {
            eyebrow("SALE DETAILS")
            copyText("Create customer sale", 17f, true)
        }
        cardHeading.addView(headingCopy, LinearLayout.LayoutParams(0, -2, 1f))
        cardHeading.addView(com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = requireContext().tr("New customer"); textSize = 13f; setIconResource(R.drawable.ic_new_sale)
            iconTint = ColorStateList.valueOf(context.ink(R.color.workspace_muted)); setTextColor(context.ink(R.color.workspace_muted))
            backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT); strokeWidth = 0
            setOnClickListener { newCustomer() }
        }, LinearLayout.LayoutParams(-2, ui.dp(44)))
        card.addView(cardHeading, LinearLayout.LayoutParams(-1, -2))
        card.stepper(1, ui.dp(14))
        card.copyText("Information", 18f)
        card.copyText("Add the customer and payment details.", 12f, color = R.color.workspace_muted)
        card.addView(View(requireContext()).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, ui.dp(1)).apply {
            topMargin = ui.dp(10); bottomMargin = ui.dp(10)
        })

                b.form.copyText("Quantity", 18f, true)
        card.eyebrow("CUSTOMER")
        val customerPicker = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; surface(); minimumHeight = context.dp(52)
            setPadding(context.dp(12), 0, context.dp(12), 0); isClickable = true; isFocusable = true
            setOnClickListener { toggleCustomerPopup(this, vm.state.value) }
        }
        customerPicker.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_search); imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_muted))
        }, LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)).apply { marginEnd = ui.dp(10) })
        customerPicker.addView(TextView(requireContext()).apply {
            text = requireContext().tr(if (customer.empty) "Search by customer name or code" else "${customer.text("code")} · ${customer.name}")
            textSize = 13f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(context.ink(if (customer.empty) R.color.workspace_muted else R.color.workspace_text))
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        customerPicker.addView(ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_chevron_right); rotation = 90f; imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_muted))
        }, LinearLayout.LayoutParams(ui.dp(22), ui.dp(22)))
        card.addView(customerPicker, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(6) })
        fun choices(title: String, values: List<Triple<String, String, String>>, selected: String, select: (String) -> Unit) {
            card.copyText(title, 13f, true).apply { setPadding(0, context.dp(10), 0, 0) }
            val grid = card.grid(125, 2)
            values.forEach { (key, label, detail) ->
                val active = key == selected
                val cell = grid.cell().apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = context.dp(72)
                    setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8)); isClickable = true; isFocusable = true
                    background = GradientDrawable().apply {
                        cornerRadius = context.dp(6).toFloat(); setColor(context.ink(if (active) R.color.workspace_tint else R.color.workspace_surface))
                        setStroke(context.dp(1), context.ink(if (active) R.color.workspace_accent else R.color.workspace_line))
                    }
                    setOnClickListener { select(key) }
                }
                cell.addView(ImageView(requireContext()).apply {
                    setImageResource(if (key == "credit") R.drawable.ic_customer else if (key == "cash") R.drawable.ic_cash else R.drawable.ic_sales)
                    imageTintList = ColorStateList.valueOf(context.ink(if (active) R.color.workspace_accent else R.color.workspace_muted))
                }, LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)).apply { marginEnd = ui.dp(8) })
                cell.addView(requireContext().column().apply {
                    copyText(label, 14f, true, if (active) R.color.workspace_accent else R.color.workspace_muted)
                    copyText(detail, 11f, color = R.color.workspace_muted)
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
        choices("Payment type", listOf(
            Triple("cash", "Paid now", "Cash or banking"),
            Triple("credit", "Credit", "Use available credit")
        ), d.paymentType) { value ->
            if (value == "credit" && !customer.flag("credit_allowed")) vm.error("Select a customer with available credit.")
            else vm.change(true) { it.copy(paymentType = value) }
        }
        if (d.paymentType == "cash") {
            val methods = s.options.rows("payment_methods")
            choices("Payment method", methods.map { method ->
                Triple(method.text("key"), method.name, if (method.flag("adds_to_cash_hold")) "Adds to your cash hold" else "Paid directly; not held as cash")
            }, d.paymentMethod) { value -> vm.change(true) { it.copy(paymentMethod = value) } }
        }
        if (customer.empty) {
            val empty = requireContext().column(10).apply { gravity = Gravity.CENTER; surface(); setBackgroundColor(context.ink(R.color.workspace_background)) }
            empty.copyText("⌕  Select a customer", 14f, true, R.color.workspace_muted).apply { gravity = Gravity.CENTER }
            empty.copyText("Credit availability will appear here.", 10f, color = R.color.workspace_muted).apply { gravity = Gravity.CENTER }
            card.addView(empty, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
        } else {
            val preview = requireContext().column(10).apply {
                background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_success_tint)); setStroke(context.dp(1), context.ink(R.color.workspace_success)) }
            }
            preview.copyText(customer.text("code"), 11f, color = R.color.workspace_muted)
            preview.copyText(customer.name, 15f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            val metrics = preview.grid(105, 3)
            listOf(
                Triple("Available", money(customer.number("available_credit")), R.color.workspace_success),
                Triple("Outstanding", money(customer.number("outstanding_amount")), R.color.workspace_text),
                Triple("Credit limit", money(customer.number("credit_limit")), R.color.workspace_text)
            ).forEach { (label, value, color) -> metrics.cell().apply { copyText(label, 10f, color = R.color.workspace_muted); copyText(value, 13f, true, color) } }
            card.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
        }
        card.field("Notes", d.notes, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE) { value -> vm.change { it.copy(notes = value) } }
        if (d.id == 0L) {
            val ready = d.latitude != null
            val location = requireContext().column(10).apply {
                background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(if (ready) R.color.workspace_success_tint else R.color.workspace_background)); setStroke(context.dp(1), context.ink(if (ready) R.color.workspace_success else R.color.workspace_line)) }
            }
            location.copyText(if (ready) "✓  Device location captured" else "⌖  Device location required", 13f, true, if (ready) R.color.workspace_success else R.color.workspace_text)
            location.copyText(if (ready) "Location ready · accuracy about ${d.accuracy?.toInt()} m" else "Select Allow location so Android can ask for permission. The office will receive this point with the sale record.", 11f, color = R.color.workspace_muted)
            if (!ready) location.button("Allow location") { captureLocation() }.outlined()
            card.addView(location, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
        }
        val footer = requireContext().column(8).apply { setBackgroundColor(context.ink(R.color.workspace_background)); gravity = Gravity.END }
        footer.button("Continue to products") { vm.next() }.apply {
            backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent)); setTextColor(context.ink(R.color.workspace_surface))
        }
        card.addView(footer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(10) })
    }
    private fun products() {
        val state = vm.state.value
        val filtered = state.options.rows("products").filter { (it.name + it.text("sku")).contains(productQuery, true) }
        productAdapter.submitList(filtered.map { p ->
            val selected = state.draft.lines.any { it.productId == p.id }
            val unit = vm.defaultUnit(p)
            val regionId = vm.customer().obj("region").id
            val price = unit.rows("prices").find { it.number("region_id") == regionId }?.number("price") ?: 0
            Card("product${p.id}", p.name, money(price), p.text("sku"),
                listOf(CardAction("Select product", "toggle", p.id)),
                kind = CardKind.PRODUCT_PICKER, status = if (selected) "selected" else "",
                featured = selected, metrics = listOf(
                    Metric("paid", p.number("quantity").toString(), "paid"),
                    Metric("FOC", p.number("foc_quantity").toString(), "FOC")
                ))
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
    private fun newCustomer() {
        val ui = requireContext()
        customerSheet?.dismiss()
        val frame = requireContext().column()
        val title = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        title.addView(requireContext().column().apply {
            copyText("New customer", 20f)
            copyText("The customer is assigned to your warehouse. Credit is disabled and the credit limit starts at 0.", 12f, color = R.color.workspace_muted)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val close = com.google.android.material.button.MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
            text = "×"; textSize = 26f; gravity = Gravity.CENTER; includeFontPadding = false
            setPadding(0, 0, 0, 0); contentDescription = requireContext().tr("Close")
            setOnClickListener { customerSheet?.dismiss() }
        }
        title.addView(close, LinearLayout.LayoutParams(requireContext().dp(44), requireContext().dp(44)))
        val fixedHeader = requireContext().column(12).apply { setBackgroundColor(context.ink(R.color.workspace_surface)) }
        fixedHeader.addView(title, LinearLayout.LayoutParams(-1, -2))
        fixedHeader.addView(View(requireContext()).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, ui.dp(1)).apply { topMargin = ui.dp(8) })
        frame.addView(fixedHeader, LinearLayout.LayoutParams(-1, -2))
        val fields = requireContext().column(12).apply { setBackgroundColor(context.ink(R.color.workspace_surface)) }
        val values = mutableMapOf<String, Any?>()
        fun input(key: String, label: String, default: String = "", type: Int = InputType.TYPE_CLASS_TEXT) {
            values[key] = default
            fields.field(label, default, type) { values[key] = it }
        }
        input("name", "Customer name")
        input("customer_type", "Customer type", "Shop")
        input("phone", "Phone", type = InputType.TYPE_CLASS_PHONE)
        val regions = vm.state.value.options.regionRows()
        var region = ""
        fields.choice("Region", listOf("" to "Select region") + regions.map { it.id.toString() to it.name }, region) { region = it }
        input("township", "Township")
        input("address", "Address")
        input("notes", "Notes", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        val scroll = ScrollView(requireContext()).apply {
            setBackgroundColor(context.ink(R.color.workspace_surface))
            addView(fields)
        }
        frame.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val fixedFooter = requireContext().column(10).apply {
            setBackgroundColor(context.ink(R.color.workspace_background))
            addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)))
        }
        val error = fixedFooter.copyText("", 11f, color = R.color.workspace_error).apply { isVisible = false }
        val actions = fixedFooter.grid(140, 2)
        actions.cell().button("Cancel") { customerSheet?.dismiss() }.outlined()
        actions.cell().button("Create customer") {
            if (values["name"].toString().isBlank() || region.toLongOrNull() == null) {
                error.text = requireContext().tr("Enter a customer name and select a region."); error.isVisible = true
            } else {
                vm.createCustomer(values + ("region_id" to region.toLong())); customerSheet?.dismiss()
            }
        }.apply { backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent)); setTextColor(context.ink(R.color.workspace_surface)) }
        frame.addView(fixedFooter, LinearLayout.LayoutParams(-1, -2))
        frame.layoutParams = ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.MATCH_PARENT)
        customerSheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext()).apply {
            setContentView(frame)
            setOnShowListener {
                findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                    sheet.fitTabletBottomSheet()
                    val availableHeight = (resources.displayMetrics.heightPixels * .9f).toInt()
                    sheet.layoutParams.height = if (resources.configuration.screenWidthDp >= 600) {
                        availableHeight.coerceAtMost(requireContext().dp(760))
                    } else availableHeight
                    sheet.minimumHeight = 0
                    sheet.requestLayout()
                    com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet).apply {
                        state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                        isDraggable = false
                        skipCollapsed = true
                    }
                }
            }
            setOnDismissListener { customerSheet = null }
            show()
        }
    }
    private fun toggleCustomerPopup(anchor: View, state: SaleState) {
        if (customerPopup?.isShowing == true) { customerPopup?.dismiss(); customerPopup = null; return }
        val ui = requireContext()
        val menu = ui.column(8).apply { surface(); elevation = ui.dp(8).toFloat() }
        val results = ui.column()
        fun populate(query: String) {
            customerQuery = query
            results.removeAllViews()
            val matches = state.options.rows("customers").filter {
                (it.name + " " + it.text("code") + " " + it.text("phone")).contains(query.trim(), true)
            }
            if (matches.isEmpty()) {
                results.copyText("No customers found", 12f, color = R.color.workspace_muted).apply {
                    gravity = Gravity.CENTER; setPadding(0, ui.dp(14), 0, ui.dp(14))
                }
            } else matches.forEach { option ->
                val row = ui.column().apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = ui.dp(52); setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4))
                    isClickable = true; isFocusable = true
                    setOnClickListener {
                        customerPopup?.dismiss(); customerPopup = null; customerQuery = ""
                        vm.change(true) { it.copy(customerId = option.id, paymentType = "cash") }
                    }
                }
                row.addView(ui.column().apply {
                    copyText(option.name, 13f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
                    copyText(option.text("code"), 10f, color = R.color.workspace_muted)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(ui.column().apply {
                    copyText(money(option.number("available_credit")), 12f, true).apply { gravity = Gravity.END }
                    copyText("credit available", 9f, color = R.color.workspace_muted).apply { gravity = Gravity.END }
                }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = ui.dp(8) })
                results.addView(row, LinearLayout.LayoutParams(-1, -2))
                results.addView(View(ui).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, ui.dp(1)))
            }
        }
        val search = menu.field("Search by customer name or code", customerQuery) { populate(it) }
        menu.addView(ScrollView(ui).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            addView(results)
        }, LinearLayout.LayoutParams(-1, ui.dp(280)))
        populate(customerQuery)
        val root = anchor.rootView
        val popup = android.widget.PopupWindow(menu, anchor.width, ui.dp(360), true).apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE))
            isOutsideTouchable = true
            inputMethodMode = android.widget.PopupWindow.INPUT_METHOD_NEEDED
            softInputMode = android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            elevation = ui.dp(8).toFloat()
        }
        customerPopup = popup
        fun scrollFormUp() {
            val scrollView = b.formScroll
            val anchorLocation = IntArray(2)
            val scrollLocation = IntArray(2)
            anchor.getLocationOnScreen(anchorLocation)
            scrollView.getLocationOnScreen(scrollLocation)
            val target = (scrollView.scrollY + anchorLocation[1] - scrollLocation[1] - ui.dp(24)).coerceAtLeast(0)
            scrollView.scrollTo(0, target)
        }
        fun keepBelowAnchor() {
            if (!popup.isShowing) return
            val visible = android.graphics.Rect()
            root.getWindowVisibleDisplayFrame(visible)
            val anchorLocation = IntArray(2)
            anchor.getLocationOnScreen(anchorLocation)
            val y = anchorLocation[1] + anchor.height + ui.dp(4)
            val available = visible.bottom - y - ui.dp(8)
            val height = minOf(ui.dp(360), available.coerceAtLeast(ui.dp(160)))
            popup.update(anchorLocation[0], y, anchor.width, height)
        }
        search.setOnFocusChangeListener { _, focused -> if (focused) search.post { scrollFormUp(); keepBelowAnchor() } }
        search.doAfterTextChanged { search.post { scrollFormUp(); keepBelowAnchor() } }
        popup.setOnDismissListener {
            customerPopup = null
        }
        scrollFormUp()
        val anchorLocation = IntArray(2)
        val rootLocation = IntArray(2)
        anchor.getLocationOnScreen(anchorLocation)
        root.getLocationOnScreen(rootLocation)
        popup.showAtLocation(
            root,
            Gravity.TOP or Gravity.START,
            anchorLocation[0] - rootLocation[0],
            anchorLocation[1] - rootLocation[1] + anchor.height + ui.dp(4)
        )
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

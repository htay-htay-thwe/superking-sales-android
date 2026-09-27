package com.example.superkingsale.ui

import com.example.superkingsale.data.Record
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private fun displayTimestamp(value: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date = parser.parse(value.take(19))
        if (date == null) value else SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.US).format(date)
    } catch (_: Exception) { value }
}

/** Maps the existing business content into the hierarchy shown in docs' phone/desktop references. */
internal fun workspacePresentation(screen: String, state: WorkspaceState, original: List<Card>, tab: String, scope: String = "trip"): List<Card> {
    fun action(label: String, key: String, id: Long = 0) = CardAction(label, key, id)
    fun header(title: String, eyebrow: String, detail: String = "", actions: List<CardAction> = emptyList()) =
        Card("page-header", title, detail = detail, actions = actions, kind = CardKind.HEADER, eyebrow = eyebrow)
    fun section(key: String, title: String, eyebrow: String = "", actions: List<CardAction> = emptyList()) =
        Card(key, title, actions = actions, kind = CardKind.SECTION, eyebrow = eyebrow)
    fun metrics(vararg values: Metric, featured: Boolean = false) = Card("metrics", "", kind = CardKind.METRICS, metrics = values.toList(), featured = featured)
    fun group(key: String, title: String, eyebrow: String, rows: List<Card>, actions: List<CardAction> = emptyList()) =
        Card(key, title, kind = CardKind.GROUP, eyebrow = eyebrow, actions = actions,
            children = rows.ifEmpty { listOf(Card("$key-empty", "No records", detail = "No records match this view.", kind = CardKind.EMPTY)) })
    fun row(card: Card) = card.copy(kind = CardKind.ROW)
    fun sales(rows: List<Record>, cards: List<Card>): List<Card> = cards.map { c ->
        val sale = rows.find { "sale${it.id}" == c.key }
        if (sale == null) row(c) else {
            val units = listOf(sale.number("total_quantity"), sale.number("units_sold"), sale.rows("items").sumOf { it.number("quantity") })
                .firstOrNull { it > 0 } ?: 0
            c.copy(title = sale.text("reference"), value = money(sale.number("total_amount")),
                detail = listOf(sale.obj("customer").name, displayTimestamp(sale.text("created_at"))).filter { it.isNotBlank() }.joinToString(" · "),
                eyebrow = "${number(units)} units · ${sale.text("payment_type")}", status = sale.text("status"), kind = CardKind.SALE_PREVIEW)
        }
    }
    val controls = Card("view-controls", "", kind = CardKind.CONTROLS)
    val result = mutableListOf<Card>()
    val data = state.data
    val record = data.obj("data")
    val pages = original.filter { it.key == "pages" }.map { it.copy(kind = CardKind.SECTION) }
    when (screen) {
        "home" -> {
            val k = data.obj("kpis")
            val representative = data.obj("representative")
            result += Card("home-header", "Route overview", detail = displayTimestamp(data.text("as_of")), status = representative.text("code"), kind = CardKind.HOME_HEADER)
            result += metrics(Metric("Cash hold", money(k.number("cash_hold")), "MMK currently in custody", true),
                Metric("Today's sales", money(k.number("today_sales")), "${number(k.number("today_cash_sales"))} cash · ${number(k.number("today_credit_sales"))} credit"),
                Metric("Current stock", number(k.number("stock_units")), "${k.number("stock_products")} products on hand"), featured = true)
            result += Card("new-sale", "Create new sale", detail = "Cash or customer credit", actions = listOf(action("New sale", "new_sale")), kind = CardKind.CTA)
            val recentSales = data.rows("recent_sales").take(5).map { sale ->
                val source = original.find { it.key == "sale${sale.id}" }
                val units = sale.number("total_base_quantity").takeIf { it > 0 } ?: sale.number("total_quantity")
                Card("sale${sale.id}", sale.text("reference"), money(sale.number("total_amount")), sale.obj("customer").name,
                    source?.actions.orEmpty(), CardKind.SALE_PREVIEW, eyebrow = "${number(units)} units · ${sale.text("payment_type")}", status = sale.text("status"))
            }
            result += group("recent-sales", "Recent sales", "OWN TRANSACTIONS", recentSales, listOf(action("View sales history", "sales")))
            val stockRows = data.rows("stock").take(6).map { stock ->
                val product = stock.obj("product")
                val unit = product.rows("units").find { it.flag("is_default_selling") }
                val factor = maxOf(1L, unit?.number("conversion_factor") ?: 1L)
                Card("stock${stock.id}", product.name, "${number(stock.number("quantity") / factor)} ${unit?.name ?: product.text("base_unit")}",
                    product.text("sku"), kind = CardKind.ROW)
            }
            val receivingRows = data.rows("pending_receivings").map { receiving ->
                val productCount = listOf(receiving.number("product_count"), receiving.number("products_count"), receiving.number("items_count"), receiving.rows("items").size.toLong()).firstOrNull { it > 0 } ?: 0
                Card("receive${receiving.id}", receiving.text("reference"), detail = listOf(receiving.obj("warehouse").name,
                    "$productCount products", "${receiving.number("total_quantity")} units").filter { it.isNotBlank() }.joinToString(" · "),
                    actions = listOf(action("Open receiving", "receiving", receiving.id)), kind = CardKind.ROW)
            }
            result += group("stock-preview", "My stock", "INVENTORY CUSTODY", stockRows, listOf(action("View all", "stock")))
            result += group("receivings-preview", "Pending stock", "RECEIVING", receivingRows).copy(status = "${receivingRows.size} pending")
            result += Card("sales-shortcut", "Sales", detail = "Review and filter your sales by trip, date, customer, product, and payment type.",
                actions = listOf(action("Open sales", "sales")), kind = CardKind.NAV_CARD)
        }
        "trip" -> {
            result += Card("page-header", "Current trip", detail = "Stock, selling, expenses, cash, and return progress for this assignment.", kind = CardKind.TRIP_HEADER, eyebrow = "FIELD OPERATION")
            if (record.empty) return result + original
            val f = record.obj("financial_summary")
            result += Card("trip-hero", record.text("reference"), value = record.name,
                detail = listOf(record.obj("region").name, record.obj("warehouse").name).filter { it.isNotBlank() }.joinToString(" / ") + "\n" +
                    listOf(record.obj("vehicle").text("vehicle_number"), record.obj("vehicle").text("vehicle_type")).filter { it.isNotBlank() }.joinToString(" / "),
                status = record.text("status"), kind = CardKind.TRIP_HERO)
            result += Card("trip-metrics", "", kind = CardKind.TRIP_METRICS, metrics = listOf(
                Metric("Stock held", number(record.number("current_stock_units")), "Paid + FOC base units"),
                Metric("Paid sales", money(f.number("cash_sales")), "Cash + banking"),
                Metric("Cash held", money(f.number("current_cash_hold")), "Before office confirmation"),
                Metric("Latest credit", money(f.number("latest_credit_balance")), "Trip customers outstanding")))
            val paymentRows = f.rows("payment_method_totals").map { method ->
                Card("method${method.text("key")}", method.name, money(method.number("total_amount")),
                    if (method.flag("adds_to_cash_hold")) "Included in cash hold" else "Direct / banking",
                    kind = CardKind.PAYMENT_METHOD, metrics = listOf(
                        Metric("Sales", money(method.number("sales_amount"))),
                        Metric("Credit collected", money(method.number("collection_amount")))))
            }
            result += Card("payment-methods", "Payments by method", detail = "Paid sales + customer credit collections",
                eyebrow = "MONEY RECEIVED", kind = CardKind.PAYMENT_GROUP, children = paymentRows)
            result += Card("trip-actions", "", kind = CardKind.ACTION_GRID, actions = listOf(
                action("New sale", "new_sale"), action("View stock", "stock"), action("Return cash", "cash"),
                action("Record expense", "expense", record.id)))
            val tripSales = sales(record.rows("sales"), original.filter { it.key.startsWith("sale") })
            val expenseRows = record.rows("expenses")
            val tripExpenses = original.filter { it.key.startsWith("expense") }.map { card ->
                val expense = expenseRows.find { "expense${it.id}" == card.key }
                if (expense == null) row(card) else card.copy(
                    detail = listOf(displayTimestamp(expense.text("spent_at")), expense.text("notes"))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    kind = CardKind.ROW
                )
            }
            result += Card("trip-sales", "Recent sales", kind = CardKind.GROUP, eyebrow = "TRIP ACTIVITY", children =
                tripSales.ifEmpty { listOf(Card("trip-sales-empty", "No sales yet", detail = "Posted sales for this trip will appear here.", kind = CardKind.EMPTY)) })
            result += Card("trip-expenses", "Trip expenses", kind = CardKind.GROUP, eyebrow = "EXPENSE HISTORY", children =
                tripExpenses.ifEmpty { listOf(Card("trip-expenses-empty", "No expenses", detail = "Keep this simple ledger for costs recorded during operation.", kind = CardKind.EMPTY)) })
            original.find { it.key == "commands" }?.actions?.filter { it.key == "end" }?.takeIf { it.isNotEmpty() }?.let {
                result += Card("ending-action", "Ready to finish selling?", detail = "Beginning ending blocks new sales and opens stock return and cash handover.", actions = it, kind = CardKind.DANGER_PANEL)
            }
            result += original.filter { it.key == "ending" }.map { it.copy(kind = CardKind.DANGER_PANEL) }
        }
        "stock" -> {
            val pendingRows = state.extra.rows("data")
            val inventoryMeta = data.obj("meta")
            fun pagination(meta: Record, fallbackTotal: Int, actions: List<CardAction>): Card {
                val current = maxOf(1L, meta.number("current_page").takeIf { it > 0 } ?: 1L)
                val last = maxOf(1L, meta.number("last_page").takeIf { it > 0 } ?: 1L)
                val total = meta.number("total").takeIf { it > 0 } ?: fallbackTotal.toLong()
                val from = meta.number("from").takeIf { it > 0 } ?: if (total > 0) 1 else 0
                val to = meta.number("to").takeIf { it > 0 } ?: fallbackTotal.toLong()
                return Card("stock-pagination-$current-$last", "", value = "Page $current of $last", detail = "$from–$to of $total",
                    actions = actions, kind = CardKind.PAGINATION)
            }
            result += Card("stock-header", "My stock", eyebrow = "Inventory custody", status = "${pendingRows.size} pending", kind = CardKind.STOCK_HEADER)
            val k = data.obj("summary").takeUnless { it.empty } ?: state.options.obj("summary")
            result += Card("stock-on-hand", "", kind = CardKind.STOCK_SUMMARY, featured = true,
                metrics = listOf(Metric("On hand", number(k.number("on_hand")), "Units available for sales")))
            result += Card("stock-incoming", "", kind = CardKind.STOCK_SUMMARY,
                metrics = listOf(Metric("Incoming", number(k.number("incoming")), "Units awaiting confirmation")))
            result += Card("stock-tabs-$tab", "", status = tab, kind = CardKind.CONTROLS)
            if (tab == "stock") result += group("stock-receivings", "Pending stock", "RECEIVING", pendingRows.map { r ->
                val products = listOf(r.number("product_count"), r.number("products_count"), r.number("items_count"), r.rows("items").size.toLong()).firstOrNull { it > 0 } ?: 0
                Card("incoming${r.id}", r.text("reference"), detail = "${r.obj("source_warehouse").name}\n$products products · ${r.number("total_quantity")} units",
                    actions = listOf(action("View receiving", "receiving", r.id)), kind = CardKind.ROW,
                    status = if (r.text("status") == "dispatched") "In transit" else r.text("status"))
            } + pagination(state.extra.obj("meta"), pendingRows.size, emptyList()), listOf(action("View history", "stock_history"))).copy(status = "${pendingRows.size} transfers")
            val inventoryRows = original.filter { it.key !in listOf("summary", "pages") }.map { c ->
                val stock = data.rows("data").find { "stock${it.id}" == c.key }
                if (stock == null) row(c) else c.copy(value = "", detail = stock.obj("product").text("sku"), kind = CardKind.TABLE_ROW,
                    metrics = listOf(Metric("Paid", c.value), Metric("FOC", number(stock.number("foc_quantity"))), Metric("Incoming", number(stock.number("pending_quantity")))))
            }
            if (tab == "stock") {
                result += group("inventory-table", "Available products", "AVAILABLE INVENTORY",
                    listOf(Card("stock-search", "", kind = CardKind.CONTROLS)) + inventoryRows +
                        pagination(inventoryMeta, inventoryRows.size, pages.flatMap { it.actions }))
                    .copy(detail = "Read only · paid and FOC balances in base units")
            }
            else {
                val historyRows = data.rows("data").map { r ->
                    val products = listOf(r.number("product_count"), r.number("products_count"), r.number("items_count"), r.rows("items").size.toLong()).firstOrNull { it > 0 } ?: 0
                    Card("issue${r.id}", r.text("reference"), detail = listOf(
                        r.obj("source_warehouse").name.ifBlank { r.obj("warehouse").name },
                        "$products products · ${r.number("total_quantity")} units",
                        r.text("received_at").ifBlank { r.text("dispatched_at") }
                    ).filter { it.isNotBlank() }.joinToString("\n"),
                        actions = listOf(action("View receiving", "receiving", r.id)), kind = CardKind.ROW,
                        status = if (r.text("status") == "received") "received" else r.text("status"))
                }
                result += group("stock-history", "Completed stock issues", "INVENTORY CUSTODY",
                    historyRows + pagination(inventoryMeta, historyRows.size, pages.flatMap { it.actions }))
                    .copy(status = "${inventoryMeta.number("total").takeIf { it > 0 } ?: historyRows.size.toLong()} records")
            }
        }
        "sales" -> {
            val k = data.obj("summary")
            val saleRows = data.rows("data")
            val meta = data.obj("meta")
            result += header("Sales history", "SALES WORKSPACE")
            result += metrics(Metric("Gross sales", money(k.number("gross_sales")), highlight = true), Metric("Cash sales", money(k.number("cash_sales"))),
                Metric("Credit sales", money(k.number("credit_sales"))), Metric("Units sold", number(k.number("units_sold"))))
            val current = maxOf(1L, meta.number("current_page").takeIf { it > 0 } ?: 1L)
            val last = maxOf(1L, meta.number("last_page").takeIf { it > 0 } ?: 1L)
            val total = meta.number("total").takeIf { it > 0 } ?: saleRows.size.toLong()
            val from = meta.number("from").takeIf { it > 0 } ?: if (total > 0) ((current - 1) * saleRows.size + 1) else 0
            val to = meta.number("to").takeIf { it > 0 } ?: minOf(total, from + saleRows.size - 1)
            val pagination = Card("sales-pagination-$current-$last", "", value = "Page $current of $last", detail = "$from–$to of $total",
                actions = pages.flatMap { it.actions }, kind = CardKind.PAGINATION)
            result += group("sales-activity", "Sales activity", "OWN TRANSACTIONS",
                listOf(controls) + sales(saleRows, original.filter { it.key !in listOf("summary", "pages") }) + pagination,
                listOf(action("New sale", "new_sale")))
        }
        "cash" -> {
            val cash = state.options; val trip = state.extra; val f = trip.obj("financial_summary")
            result += header("Cash hold", "TRIP SETTLEMENT", actions = original.first { it.key == "hold" }.actions).copy(key = "cash-header")
            result += Card("active-trip", "Current trip", value = listOf(trip.text("reference", "No active trip"), trip.name).filter { it.isNotBlank() }.joinToString(" · "),
                detail = listOf(trip.obj("warehouse").text("code"), trip.obj("warehouse").name.ifBlank { trip.obj("region").name }).filter { it.isNotBlank() }.joinToString(" · "),
                status = trip.text("status"), kind = CardKind.TRIP_HERO)
            result += metrics(
                Metric("Current hold", money(cash.number("cash_hold")), "MMK in your custody", highlight = true),
                Metric("Available to return", money(cash.number("available_to_submit")), "After pending handovers"),
                Metric("Sales added to hold", money(f.number("cash_hold_sales")), "Cash-custody methods only"),
                Metric("Confirmed returned", money(f.number("cash_submitted_confirmed")), "Accepted by the office"))
            result += Card("cash-breakdown", "", kind = CardKind.CASH_BREAKDOWN, metrics = listOf(
                Metric("Opening cash", money(trip.number("opening_cash_balance"))), Metric("Credit collected", money(f.number("cash_credit_collected"))),
                Metric("Pending handover", money(cash.number("pending_submissions"))), Metric("Trip responsibility", money(maxOf(0, trip.number("opening_cash_balance") + f.number("cash_hold_sales") + f.number("cash_credit_collected") - f.number("cash_submitted_confirmed"))))))
            result += Card("cash-tabs-$tab", "", kind = CardKind.CONTROLS)
            val cashRows = original.filter { it.key !in listOf("hold", "trip-cash", "pages") }.map { c ->
                val r = data.rows("data").find { "cash${it.id}" == c.key || "ledger${it.id}" == c.key }
                if (c.key == "empty") c.copy(
                    title = if (tab == "ledger") "No ledger entries yet" else "No cash returns yet",
                    detail = if (tab == "ledger") "Cash custody activity will appear here." else "Cash returned during this trip will appear here.",
                    kind = CardKind.EMPTY
                ) else if (r == null) row(c) else if (tab == "ledger") c.copy(
                    title = r.text("reference"), value = money(r.number("amount_delta")),
                    detail = listOf(r.text("type").replace('_', ' '), displayTimestamp(r.text("occurred_at"))).filter { it.isNotBlank() }.joinToString(" · "),
                    kind = CardKind.CASH_LEDGER
                ) else c.copy(
                    title = r.text("reference"), value = money(r.number("amount")),
                    detail = listOf(r.obj("trip").text("reference"), displayTimestamp(r.text("created_at"))).filter { it.isNotBlank() }.joinToString(" · "),
                    status = r.text("status"), kind = CardKind.CASH_RETURN
                )
            }
            val meta = data.obj("meta")
            val current = maxOf(1L, meta.number("current_page").takeIf { it > 0 } ?: 1L)
            val last = maxOf(1L, meta.number("last_page").takeIf { it > 0 } ?: 1L)
            val total = meta.number("total").takeIf { it > 0 } ?: data.rows("data").size.toLong()
            val from = meta.number("from").takeIf { it > 0 } ?: if (total > 0) 1 else 0
            val to = meta.number("to").takeIf { it > 0 } ?: data.rows("data").size.toLong()
            val pagination = Card("cash-pagination-$current-$last", "", value = "Page $current of $last", detail = "$from–$to of $total",
                actions = pages.flatMap { it.actions }, kind = CardKind.PAGINATION)
            val recordChildren = (if (tab == "returns") listOf(Card("cash-scope-$scope", "", kind = CardKind.CONTROLS)) else emptyList()) + cashRows + pagination
            result += group("cash-records", if (tab == "ledger") "All custody activity" else "Cash returns",
                if (tab == "ledger") "APPEND-ONLY LEDGER" else "OFFICE HANDOVERS", recordChildren)
                .copy(status = "$total ${if (tab == "ledger") "entries" else "records"}",
                    detail = if (tab == "ledger") "Sales, office confirmations, and reversals remain visible across every trip." else "")
        }
        "customers" -> {
            result += Card("customers-header", "Customers", actions = listOf(action("New customer", "customer_new")), kind = CardKind.HEADER, eyebrow = "ROUTE CUSTOMERS")
            val customerRows = original.filter { it.key !in listOf("new", "pages", "empty") }.map { c ->
                val r = data.rows("data").find { "customer${it.id}" == c.key }
                if (r == null) row(c) else c.copy(title = r.name, value = r.text("phone", "No phone"),
                    detail = listOf("${r.text("code")} · ${r.text("customer_type")}",
                        listOf(r.obj("warehouse").name.ifBlank { state.extra.obj("warehouse").name }, r.obj("region").name).filter { it.isNotBlank() }.joinToString(" · "),
                        r.text("township")).filter { it.isNotBlank() }.joinToString("\n"),
                    status = if (r.number("outstanding_amount") > 0) "Credit due ${money(r.number("outstanding_amount"))}" else "No credit due", kind = CardKind.ROW)
            }
            val meta = data.obj("meta")
            val current = maxOf(1L, meta.number("current_page").takeIf { it > 0 } ?: 1L)
            val last = maxOf(1L, meta.number("last_page").takeIf { it > 0 } ?: 1L)
            val total = meta.number("total").takeIf { it > 0 } ?: data.rows("data").size.toLong()
            val from = meta.number("from").takeIf { it > 0 } ?: if (total > 0) 1 else 0
            val to = meta.number("to").takeIf { it > 0 } ?: data.rows("data").size.toLong()
            val pagination = Card("customers-pagination-$current-$last", "", value = "Page $current of $last", detail = "$from–$to of $total",
                actions = pages.flatMap { it.actions }, kind = CardKind.PAGINATION)
            result += group("customer-list", "Customer list", "ASSIGNED WAREHOUSE", listOf(controls) + customerRows + pagination)
                .copy(status = "$total customers")
        }
        "sale_detail" -> {
            val sale = original.first { it.key == "sale" }
            val location = record.obj("creation_location")
            val locationActions = if (location.empty) emptyList() else listOf(action("View sale location", "map"))
            result += Card("sale-header", record.text("reference"), eyebrow = "Sales", detail = record.obj("customer").name + " · " + displayTimestamp(record.text("created_at")),
                actions = sale.actions, status = record.text("status"), kind = CardKind.SALE_DETAIL_HEADER)
            result += Card("transaction", "Sale information", eyebrow = "TRANSACTION", detail = record.text("notes"), actions = locationActions, kind = CardKind.SALE_DETAIL_TRANSACTION, metrics = listOf(
                Metric("Customer", record.obj("customer").name, listOf(record.obj("customer").text("phone"), record.obj("customer").text("address")).filter { it.isNotBlank() }.joinToString("\n")),
                Metric("Payment type", "${record.text("payment_type").replaceFirstChar { it.uppercase() }} · ${record.text("payment_method_name")}"),
                Metric("Warehouse", record.obj("warehouse").name), Metric("Region", record.obj("region").name),
                Metric("Trip", record.obj("trip").text("reference")), Metric("Created", displayTimestamp(record.text("created_at"))),
                Metric("Location", record.obj("creation_location").let { location ->
                    if (location.empty) "—" else "${location.text("latitude")}, ${location.text("longitude")}"
                })))
            val items = record.rows("items")
            val focUnits = items.sumOf { it.number("foc_quantity") }
            result += group("products", "Line items", "PRODUCTS SOLD", items.map { item ->
                Card("item${item.id}", item.obj("product").name, detail = item.obj("product").text("sku"), kind = CardKind.TABLE_ROW,
                    metrics = listOf(
                        Metric("Paid", "${item.number("quantity")} ${item.obj("unit").name}"),
                        Metric("FOC", "${item.number("foc_quantity")} ${item.obj("foc_unit").name}"),
                        Metric("Unit price", money(item.number("unit_price"))),
                        Metric("Total", money(item.number("line_total")), if (item.number("discount_amount") > 0) "-${money(item.number("discount_amount"))}" else "")
                        ))
            } + Card("products-total", "", kind = CardKind.SALE_DETAIL_TOTAL, metrics = listOf(
                Metric("Products", number(items.size.toLong())),
                Metric("Discount", money(record.number("total_discount"))),
                Metric("Promotion", money(record.number("total_item_promotion") + record.number("promotion_amount"))),
                Metric("Cashback", money(record.number("cashback_amount"))),
                Metric("Invoice total", money(record.number("total_amount")))))).copy(status = "${number(items.size.toLong())} sold · ${number(focUnits)} FOC")
            result += original.filter { it.key !in listOf("sale", "totals", "location", "audit") && !it.key.startsWith("item") }
        }
        "receiving" -> {
            val receivingStatus = record.text("status")
            result += header(record.text("reference"), "STOCK RECEIVING", record.obj("source_warehouse").name + " · Dispatched " + displayTimestamp(record.text("dispatched_at")))
                .copy(status = if (receivingStatus == "received") "Received" else receivingStatus)
            val shipmentRows = record.rows("items").map { item ->
                Card("item${item.id}", item.obj("product").name, detail = item.obj("product").text("sku"), kind = CardKind.SHIPMENT_ROW,
                    metrics = listOf(
                        Metric("Paid", "${item.number("quantity")} ${item.obj("unit").name}", "${item.number("base_quantity")} base"),
                        Metric("FOC", "${item.number("foc_quantity")} ${item.obj("foc_unit").name}", "${item.number("foc_base_quantity")} base"),
                        Metric("Total base", number(item.number("base_quantity") + item.number("foc_base_quantity")))
                    ))
            }
            val paidBase = record.rows("items").sumOf { it.number("base_quantity") }
            val focBase = record.rows("items").sumOf { it.number("foc_base_quantity") }
            result += group("shipment", "${record.rows("items").size} products · ${record.number("total_quantity")} base units", "SHIPMENT CONTENTS", shipmentRows +
                Card("shipment-total", "Total base", kind = CardKind.SHIPMENT_TOTAL,
                    metrics = listOf(Metric("Paid", number(paidBase)), Metric("FOC", number(focBase)), Metric("Total base", number(paidBase + focBase)))))
            result += original.filter { it.key !in listOf("transfer", "audit") && !it.key.startsWith("item") }
        }
        else -> result += original
    }
    return result + if (screen in listOf("stock", "sales", "cash", "customers")) emptyList() else pages
}

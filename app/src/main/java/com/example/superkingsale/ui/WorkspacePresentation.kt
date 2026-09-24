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
internal fun workspacePresentation(screen: String, state: WorkspaceState, original: List<Card>, tab: String): List<Card> {
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
        if (sale == null) row(c) else c.copy(title = sale.text("reference"), detail =
            "${sale.obj("customer").name}\n${sale.text("created_at")} · ${sale.text("payment_type")} · ${sale.text("payment_method_name")}",
            status = sale.text("status"), kind = CardKind.ROW)
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
            val tripExpenses = original.filter { it.key.startsWith("expense") }.map(::row)
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
            result += header("My stock", "ROUTE INVENTORY", "Stock in your custody and incoming issues.")
            if (tab == "stock") {
                val k = data.obj("summary")
                result += metrics(Metric("On hand", number(k.number("on_hand")), "FOC: ${number(k.number("foc_on_hand"))}"), Metric("Incoming", number(k.number("incoming")), highlight = true))
            }
            result += controls
            if (tab == "stock") result += group("stock-receivings", "Pending receivings", "RECEIVING", state.extra.rows("data").map { r ->
                Card("incoming${r.id}", r.text("reference"), detail = "${r.obj("source_warehouse").name} · ${r.number("total_quantity")} units",
                    actions = listOf(action("View receiving", "receiving", r.id)), kind = CardKind.ROW, status = r.text("status"))
            })
            val inventoryRows = original.filter { it.key !in listOf("summary", "pages") }.map { c ->
                val stock = data.rows("data").find { "stock${it.id}" == c.key }
                if (stock == null) row(c) else c.copy(value = "", detail = stock.obj("product").text("sku"), kind = CardKind.TABLE_ROW,
                    metrics = listOf(Metric("Paid", c.value), Metric("FOC", number(stock.number("foc_quantity"))), Metric("Incoming", number(stock.number("pending_quantity")))))
            }
            if (tab == "stock") result += group("inventory-table", "Available products", "AVAILABLE INVENTORY", inventoryRows)
            else {
                result += section("inventory-heading", if (tab == "pending") "Pending receivings" else "Issue history", "STOCK BALANCE")
                result += inventoryRows
            }
        }
        "sales" -> {
            val k = data.obj("summary")
            result += header("Sales history", "SALES WORKSPACE")
            result += metrics(Metric("Gross sales", money(k.number("gross_sales")), highlight = true), Metric("Cash sales", money(k.number("cash_sales"))),
                Metric("Credit sales", money(k.number("credit_sales"))), Metric("Units sold", number(k.number("units_sold"))))
            result += section("sales-heading", "Sales activity", "OWN TRANSACTIONS", listOf(action("New sale", "new_sale")))
            result += controls
            result += sales(data.rows("data"), original.filter { it.key !in listOf("summary", "pages") })
        }
        "cash" -> {
            val cash = state.options; val trip = state.extra; val f = trip.obj("financial_summary")
            result += header("Cash hold", "TRIP SETTLEMENT", actions = original.first { it.key == "hold" }.actions)
            result += Card("active-trip", trip.text("reference", "No active trip"), detail = trip.name + " · " + trip.obj("region").name, eyebrow = "ACTIVE TRIP", status = trip.text("status"))
            result += metrics(Metric("Current hold", money(cash.number("cash_hold")), highlight = true), Metric("Available to return", money(cash.number("available_to_submit"))),
                Metric("Sales added to hold", money(f.number("cash_hold_sales"))), Metric("Confirmed returned", money(f.number("cash_submitted_confirmed"))))
            result += Card("cash-breakdown", "", detail = "Cash hold changes only after office confirmation.", metrics = listOf(
                Metric("Opening cash", money(trip.number("opening_cash_balance"))), Metric("Credit collected", money(f.number("cash_credit_collected"))),
                Metric("Pending handover", money(cash.number("pending_submissions"))), Metric("Trip responsibility", money(maxOf(0, trip.number("opening_cash_balance") + f.number("cash_hold_sales") + f.number("cash_credit_collected") - f.number("cash_submitted_confirmed"))))))
            result += controls
            result += section("cash-heading", if (tab == "ledger") "Cash ledger" else "Cash returns", "OFFICE HANDOVERS")
            result += original.filter { it.key !in listOf("hold", "trip-cash", "pages") }.map { c ->
                val r = data.rows("data").find { "cash${it.id}" == c.key }
                row(c).copy(status = r?.text("status").orEmpty())
            }
        }
        "customers" -> {
            result += header("Customers", "ROUTE CUSTOMERS", actions = listOf(action("New customer", "customer_new")))
            result += section("customers-heading", "Customer list", "ASSIGNED WAREHOUSE")
            result += controls
            result += original.filter { it.key !in listOf("new", "pages") }.map { c ->
                val r = data.rows("data").find { "customer${it.id}" == c.key }
                if (r == null) row(c) else c.copy(title = r.name, value = r.text("phone", "No phone"),
                    detail = listOf(r.text("code"), r.text("customer_type"), r.obj("region").name, r.text("township")).filter { it.isNotBlank() }.joinToString(" · "),
                    status = if (r.number("outstanding_amount") > 0) "Credit due ${money(r.number("outstanding_amount"))}" else "No credit due", kind = CardKind.ROW)
            }
        }
        "sale_detail" -> {
            val sale = original.first { it.key == "sale" }
            result += header(record.text("reference"), "SALE DETAILS", record.obj("customer").name + " · " + record.text("created_at"), sale.actions).copy(status = record.text("status"))
            result += Card("transaction", "Transaction information", detail = record.text("notes"), metrics = listOf(
                Metric("Customer", record.obj("customer").name, record.obj("customer").text("phone") + "\n" + record.obj("customer").text("address")),
                Metric("Payment", record.text("payment_type"), record.text("payment_method_name")), Metric("Warehouse", record.obj("warehouse").name),
                Metric("Region", record.obj("region").name), Metric("Trip", record.obj("trip").text("reference")), Metric("Created", record.text("created_at"))))
            result += group("products", "Line items", "PRODUCTS SOLD", record.rows("items").map { item ->
                Card("item${item.id}", item.obj("product").name, detail = item.obj("product").text("sku"), kind = CardKind.TABLE_ROW,
                    metrics = listOf(
                        Metric("Paid", "${item.number("quantity")} ${item.obj("unit").name}"),
                        Metric("FOC", "${item.number("foc_quantity")} ${item.obj("foc_unit").name}"),
                        Metric("Unit price", money(item.number("unit_price"))),
                        Metric("Total", money(item.number("line_total")), if (item.number("discount_amount") > 0) "-${money(item.number("discount_amount"))}" else "")
                    ))
            })
            result += original.filter { it.key != "sale" && !it.key.startsWith("item") }
        }
        "receiving" -> {
            result += header(record.text("reference"), "STOCK RECEIVING", record.obj("source_warehouse").name + " · " + record.text("dispatched_at")).copy(status = record.text("status"))
            original.find { it.key == "transfer" }?.let { transfer ->
                result += transfer.copy(key = "shipment-summary", title = "Shipment overview", kind = CardKind.PANEL)
            }
            val shipmentRows = record.rows("items").map { item ->
                Card("item${item.id}", item.obj("product").name, detail = item.obj("product").text("sku"), kind = CardKind.TABLE_ROW,
                    metrics = listOf(
                        Metric("Paid", "${item.number("quantity")} ${item.obj("unit").name}", "${item.number("base_quantity")} base"),
                        Metric("FOC", "${item.number("foc_quantity")} ${item.obj("foc_unit").name}", "${item.number("foc_base_quantity")} base"),
                        Metric("Total base", number(item.number("base_quantity") + item.number("foc_base_quantity")))
                    ))
            }
            val paidBase = record.rows("items").sumOf { it.number("base_quantity") }
            val focBase = record.rows("items").sumOf { it.number("foc_base_quantity") }
            result += group("shipment", "${record.rows("items").size} products · ${record.number("total_quantity")} base units", "SHIPMENT CONTENTS", shipmentRows +
                Card("shipment-total", "Total base", kind = CardKind.TABLE_ROW,
                    metrics = listOf(Metric("Paid", number(paidBase)), Metric("FOC", number(focBase)), Metric("Total base", number(paidBase + focBase)))))
            result += original.filter { it.key !in listOf("transfer") && !it.key.startsWith("item") }
        }
        else -> result += original
    }
    return result + pages
}

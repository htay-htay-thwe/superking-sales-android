package com.example.superkingsale

import com.example.superkingsale.data.Record
import com.example.superkingsale.ui.*
import org.junit.Assert.*
import org.junit.Test

class WorkspacePresentationTest {
    @Test fun customerOptionsExposeRegionsAtEitherResponseLevel() {
        assertEquals(3L, Record.parse("""{"regions":[{"id":3,"name":"Yangon"}]}""").regionRows().single().id)
        assertEquals(3L, Record.parse("""{"representative":{"regions":[{"id":3,"name":"Yangon"}]}}""").regionRows().single().id)
        assertEquals(3L, Record.parse("""{"data":{"regions":[{"id":3,"name":"Yangon"}]}}""").regionRows().single().id)
    }

    @Test fun draftDetailsKeepEveryExplicitCommand() {
        val actions = listOf("edit", "post", "delete", "print").map { CardAction(it, it, 44) }
        val data = Record.parse("""{"data":{"id":44,"reference":"SAL-044","status":"draft","customer":{"name":"Shop"}}}""")
        val cards = workspacePresentation("sale_detail", WorkspaceState(data = data), listOf(Card("sale", "SAL-044", actions = actions), Card("totals", "Invoice totals")), "")
        assertEquals(actions, cards.first().actions)
        assertEquals("draft", cards.first().status)
        assertTrue(cards.any { it.key == "totals" })
    }
    @Test fun customerCollectionEligibilityRemainsInBusinessLayer() {
        val eligible = Card("customer10", "Shop", actions = listOf(CardAction("Collect credit", "collect", 10)))
        val ineligible = Card("customer11", "Cash shop")
        val data = Record.parse("""{"data":[{"id":10,"credit_allowed":true},{"id":11,"credit_allowed":false}]}""")
        val cards = workspacePresentation("customers", WorkspaceState(data = data), listOf(Card("new", "Customers"), eligible, ineligible), "")
        assertEquals(eligible.actions, cards.first { it.key == eligible.key }.actions)
        assertTrue(cards.first { it.key == ineligible.key }.actions.isEmpty())
    }
    @Test fun stockPreviewLinksToTheExactReceiving() {
        val state = WorkspaceState(data = Record.parse("""{"summary":{"on_hand":24,"foc_on_hand":2,"incoming":12}}"""),
            extra = Record.parse("""{"data":[{"id":15,"reference":"RTR-15","status":"dispatched","total_quantity":12}]}"""))
        val cards = workspacePresentation("stock", state, emptyList(), "stock")
        val preview = cards.first { it.key == "stock-receivings" }.children.first { it.key == "incoming15" }
        assertEquals(CardAction("View receiving", "receiving", 15), preview.actions.single())
        assertEquals("In transit", preview.status)
    }
    @Test fun cashSummaryDoesNotSubtractPendingHandoverTwice() {
        val state = WorkspaceState(options = Record.parse("""{"cash_hold":12000,"available_to_submit":9000,"pending_submissions":3000}"""),
            extra = Record.parse("""{"opening_cash_balance":1000,"financial_summary":{"cash_hold_sales":12000,"cash_credit_collected":2000,"cash_submitted_confirmed":3000}}"""))
        val action = CardAction("Return cash", "submitCash")
        val cards = workspacePresentation("cash", state, listOf(Card("hold", "Cash", actions = listOf(action))), "returns")
        assertEquals(listOf(action), cards.first().actions)
        val metrics = cards.first { it.key == "metrics" }.metrics
        assertEquals(money(12000), metrics.first().value)
        assertEquals(money(9000), metrics[1].value)
        assertEquals(money(12000), cards.first { it.key == "cash-breakdown" }.metrics.last().value)
    }
    @Test fun historyKeepsStockSummaryAndGroupsCompletedIssues() {
        val history = Record.parse("""{"data":[{"id":9,"reference":"RTR-9","status":"received","total_quantity":65,"product_count":13,"warehouse":{"name":"Mandalay Warehouse"}}],"meta":{"total":1}}""")
        val summary = Record.parse("""{"summary":{"on_hand":1240,"incoming":216}}""")
        val cards = workspacePresentation("stock", WorkspaceState(data = history, options = summary), emptyList(), "history")
        assertTrue(cards.any { it.key == "stock-on-hand" })
        assertTrue(cards.any { it.key == "stock-incoming" })
        val group = cards.first { it.key == "stock-history" }
        assertEquals("1 records", group.status)
        assertEquals("received", group.children.first().status)
    }
    @Test fun receivingUsesAlignedProductRowsAndTotalFooter() {
        val data = Record.parse("""{"data":{"reference":"RTR-10","total_quantity":48,"items":[{"id":3,"quantity":2,"base_quantity":48,"foc_quantity":1,"foc_base_quantity":2,"product":{"name":"Oil","sku":"SKU-3"},"unit":{"name":"box"},"foc_unit":{"name":"bottle"}}]}}""")
        val cards = workspacePresentation("receiving", WorkspaceState(data = data), listOf(Card("audit", "Receiving record")), "")
        val shipment = cards.first { it.key == "shipment" }
        assertEquals(CardKind.SHIPMENT_ROW, shipment.children.first().kind)
        assertEquals(listOf("Paid", "FOC", "Total base"), shipment.children.first().metrics.map { it.title })
        assertEquals(CardKind.SHIPMENT_TOTAL, shipment.children.last().kind)
        assertEquals("shipment-total", shipment.children.last().key)
        assertFalse(cards.any { it.key == "audit" })
    }
    @Test fun saleDetailProductsUseTheSharedTableContract() {
        val data = Record.parse("""{"data":{"reference":"SAL-1","customer":{"name":"Shop"},"items":[{"id":7,"quantity":2,"foc_quantity":0,"unit_price":1000,"line_total":2000,"product":{"name":"Oil","sku":"SKU-7"},"unit":{"name":"box"},"foc_unit":{"name":"box"}}]}}""")
        val cards = workspacePresentation("sale_detail", WorkspaceState(data = data), listOf(Card("sale", "SAL-1")), "")
        val products = cards.first { it.key == "products" }
        assertEquals(CardKind.TABLE_ROW, products.children.single().kind)
        assertEquals(listOf("Paid", "FOC", "Unit price", "Total"), products.children.single().metrics.map { it.title })
    }
    @Test fun homeUsesReactPreviewHierarchy() {
        val data = Record.parse("""{"as_of":"2026-09-18T09:11:04Z","representative":{"code":"SR-10"},"kpis":{},"recent_sales":[],"stock":[],"pending_receivings":[]}""")
        val cards = workspacePresentation("home", WorkspaceState(data = data), emptyList(), "")
        assertEquals(CardKind.HOME_HEADER, cards.first().kind)
        assertEquals(CardKind.CTA, cards.first { it.key == "new-sale" }.kind)
        assertTrue(cards.any { it.key == "stock-preview" })
        assertTrue(cards.any { it.key == "receivings-preview" })
        assertEquals(CardKind.NAV_CARD, cards.first { it.key == "sales-shortcut" }.kind)
        assertFalse(cards.any { it.key in listOf("inventory-columns", "quick-links") })
    }
    @Test fun tripUsesReactPageHierarchyWithoutTheOldSettlementPanel() {
        val data = Record.parse("""{"data":{"id":10,"reference":"TRP-10","title":"Route","status":"operation","region":{"name":"Mandalay"},"warehouse":{"name":"Main"},"vehicle":{"vehicle_number":"9P-1","vehicle_type":"Van"},"financial_summary":{"payment_method_totals":[{"key":"cash","name":"Cash","adds_to_cash_hold":true}]},"sales":[],"expenses":[]}}""")
        val original = listOf(
            Card("trip", "Route"),
            Card("commands", "Trip actions", actions = listOf(CardAction("New sale", "new_sale"), CardAction("Record expense", "expense", 10), CardAction("Begin trip ending", "end", 10))))
        val cards = workspacePresentation("trip", WorkspaceState(data = data), original, "")
        assertEquals(CardKind.TRIP_HEADER, cards.first().kind)
        assertEquals(CardKind.TRIP_HERO, cards.first { it.key == "trip-hero" }.kind)
        assertEquals(CardKind.TRIP_METRICS, cards.first { it.key == "trip-metrics" }.kind)
        assertEquals(CardKind.PAYMENT_METHOD, cards.first { it.key == "payment-methods" }.children.single().kind)
        assertEquals(listOf("new_sale", "stock", "cash", "expense"), cards.first { it.key == "trip-actions" }.actions.map { it.key })
        assertEquals(CardKind.EMPTY, cards.first { it.key == "trip-sales" }.children.single().kind)
        assertEquals(CardKind.DANGER_PANEL, cards.first { it.key == "ending-action" }.kind)
        assertFalse(cards.any { it.key in listOf("activity-columns", "settlement") })
    }
}

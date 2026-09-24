package com.example.superkingsale.ui

import android.app.Activity
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.superkingsale.data.Record
import com.example.superkingsale.ui.LocalizedDialogBuilder as MaterialAlertDialogBuilder

/** WebView is used only as Android's HTML-to-print renderer, never for application navigation. */
object InvoicePrinter {
    fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
    fun document(sale: Record, branding: Record, paper: String = "a4", translate: (String) -> String = { it }): String {
        fun e(text: String) = escape(text)
        fun t(text: String) = e(translate(text))
        fun amount(key: String) = e(money(sale.number(key)))
        val logo = branding.text("logo_url").takeIf {
            runCatching { java.net.URI(it).let { uri -> uri.scheme == "https" && uri.host == "www.superkingmyanmar.com" && uri.userInfo == null } }.getOrDefault(false)
        }
        val customer = sale.obj("customer")
        val lines = sale.rows("items").joinToString("") { line ->
            "<tr><td><b>${e(line.obj("product").name)}</b><br>${e(line.obj("product").text("sku"))}" +
            "<br>${line.number("quantity")} ${e(line.obj("unit").name)} × ${e(money(line.number("unit_price")))}" +
            "<br>${t("FOC")}: ${line.number("foc_quantity")} ${e(line.obj("foc_unit").name)}" +
            "<br>${t("Discount")}: ${e(line.text("discount_percentage"))}% (${e(money(line.number("discount_amount")))})" +
            "<br>${e(line.text("promotion_title", "Promotion"))}: ${e(money(line.number("promotion_amount")))}</td>" +
            "<td class='amount'>${e(money(line.number("line_total")))}</td></tr>"
        }
        return """<!DOCTYPE html><html><head><meta charset="utf-8"><style>
            body{font:12px sans-serif;color:#18332e;margin:16px;overflow-wrap:anywhere}
            h1{font-size:22px;margin-bottom:4px}h2{font-size:16px}p{line-height:1.6}
            table{width:100%;border-collapse:collapse}td{padding:8px 0;border-bottom:1px solid #dde4e2}
            .amount{text-align:right;white-space:nowrap}small{color:#526964}
            .total{font-size:18px;font-weight:bold}.void{color:#b42318;border:2px solid;padding:8px}
            .signatures{margin-top:24px;break-inside:avoid}
            .a5{font-size:9px}.a5 h1{font-size:17px}.a5 h2{font-size:12px}.a5 p{line-height:1.35;margin:8px 0}
            .a5 td{padding:5px 0}.a5 .total{font-size:14px}.a5 .signatures{margin-top:16px}
            .thermal{font-size:9px;width:${when(paper) { "50mm" -> "46mm"; "58mm" -> "54mm"; else -> "76mm" }};max-width:100%}.thermal h1{font-size:14px}.thermal h2{font-size:11px}
            .thermal p{line-height:1.35;margin:6px 0}.thermal .total{font-size:12px}
            .thermal table,.thermal tr,.thermal td{display:block}.thermal td{padding:3px 0}
            .thermal td.amount{text-align:right;border-bottom:1px dashed #526964}
            .thermal .signatures{margin-top:16px}.narrow{font-size:8px}
            @media print{body{margin:0}tr{break-inside:avoid}}
            </style></head><body class="${when(paper) { "a5" -> "a5"; "80mm" -> "thermal"; "58mm", "50mm" -> "thermal narrow"; else -> "a4" }}">
            ${logo?.let { "<img alt='' style='max-width:96px;max-height:64px' src='${e(it)}'>" }.orEmpty()}
            <h1>${e(branding.text("business_name", "Super King"))}</h1>
            <small>${e(branding.text("business_tagline"))}</small>
            <p>${e(branding.text("business_address", sale.obj("warehouse").text("address")))}<br>
            ${e(branding.text("business_phone", sale.obj("warehouse").text("phone")))} · ${e(branding.text("business_email"))}</p>
            <h2>${t("INVOICE")} · ${e(sale.text("reference"))}</h2>
            ${if (sale.text("status") == "voided") "<p class='void'>${t("VOID")}</p>" else ""}
            <p>${t(sale.text("status"))} · ${e(sale.text("posted_at", sale.text("created_at")))}<br>
            ${t("Payment")}: ${t(sale.text("payment_type"))} · ${e(sale.text("payment_method_name"))}<br>
            ${t("Representative")}: ${e(sale.obj("representative").name)}<br>${t("Trip")}: ${e(sale.obj("trip").text("reference"))}<br>
            ${t("Coverage")}: ${e(sale.obj("region").name)}</p>
            <h2>${t("Bill to")}</h2><p><b>${e(customer.name)}</b><br>${e(customer.text("code"))}<br>
            ${e(customer.text("phone"))}<br>${e(customer.text("address"))}</p>
            <table>$lines</table>
            <p>${t("Gross")}: ${amount("gross_amount")}<br>${t("Item discounts")}: −${amount("total_discount")}<br>
            ${t("Item promotions")}: −${amount("total_item_promotion")}<br>${e(sale.text("promotion_title", translate("Invoice promotion")))}: −${amount("promotion_amount")}<br>
            ${t("Cashback")}: −${amount("cashback_amount")}</p>
            <p class="total">${t("Payable total")}: ${amount("total_amount")}</p>
            <p>${e(sale.text("notes"))}<br>${e(sale.text("void_reason"))}</p>
            <p class="signatures">${t("Customer signature")} __________<br><br>${t("Authorized signature")} __________</p>
            <small>${e(branding.text("invoice_footer", translate("Thank you for your business")))}</small>
            </body></html>"""
    }
    fun print(activity: Activity, sale: Record, branding: Record, userId: Long, thermal: (() -> Unit)? = null) {
        val prefs = activity.getSharedPreferences("appearance", Activity.MODE_PRIVATE)
        val options = arrayOf("a4", "a5", "80mm", "58mm", "50mm")
        val labels = arrayOf("A4", "A5", "80 mm", "58 mm", "50 mm")
        var selected = options.indexOf(prefs.getString("paper.$userId", "a4")).coerceAtLeast(0)
        MaterialAlertDialogBuilder(activity).setTitle(activity.tr("Print invoice"))
            .setNeutralButton(if (thermal == null) "Paper size help" else "Connection / PDF") { _, _ ->
                if (thermal != null) { thermal(); return@setNeutralButton }
                MaterialAlertDialogBuilder(activity).setTitle("Paper size help")
                    .setMessage("Thermal sizes require a compatible print service. Check the paper size in Android's preview; unsupported sizes may fall back to Letter or A4.")
                    .setPositiveButton("OK", null).show()
            }
            .setSingleChoiceItems(labels, selected) { _, index -> selected = index }
            .setNegativeButton(activity.tr("Cancel"), null).setPositiveButton(activity.tr("Print / Save PDF")) { _, _ ->
                val paper = options[selected]
                prefs.edit().putString("paper.$userId", paper).apply()
                val web = WebView(activity)
                web.settings.javaScriptEnabled = false
                web.settings.allowFileAccess = false
                web.settings.allowContentAccess = false
                web.webViewClient = object : WebViewClient() {
                    private var launched = false
                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest) = true
                    override fun onPageFinished(view: WebView, url: String) {
                        if (launched) return
                        launched = true
                        if (activity.isFinishing || activity.isDestroyed) { web.destroy(); return }
                        val media = when (paper) {
                            "a5" -> PrintAttributes.MediaSize.ISO_A5
                            "80mm" -> PrintAttributes.MediaSize("80mm", "80 mm", 3150, 11693)
                            "58mm" -> PrintAttributes.MediaSize("58mm", "58 mm", 2283, 11693)
                            "50mm" -> PrintAttributes.MediaSize("50mm", "50 mm", 1969, 11693)
                            else -> PrintAttributes.MediaSize.ISO_A4
                        }
                        val delegate = web.createPrintDocumentAdapter(sale.text("reference"))
                        val adapter = object : android.print.PrintDocumentAdapter() {
                            override fun onStart() = delegate.onStart()
                            override fun onLayout(old: PrintAttributes?, new: PrintAttributes, signal: android.os.CancellationSignal,
                                callback: LayoutResultCallback, extras: android.os.Bundle?) = delegate.onLayout(old, new, signal, callback, extras)
                            override fun onWrite(pages: Array<out android.print.PageRange>, destination: android.os.ParcelFileDescriptor,
                                signal: android.os.CancellationSignal, callback: WriteResultCallback) = delegate.onWrite(pages, destination, signal, callback)
                            override fun onFinish() { delegate.onFinish(); web.destroy() }
                        }
                        runCatching {
                            val job = activity.getSystemService(PrintManager::class.java)?.print(sale.text("reference"), adapter,
                                PrintAttributes.Builder().setMediaSize(media).setMinMargins(PrintAttributes.Margins(79, 79, 79, 79))
                                    .setColorMode(PrintAttributes.COLOR_MODE_COLOR).build())
                            check(job != null) { "Printing is unavailable on this device." }
                        }.onFailure {
                            web.destroy()
                            android.widget.Toast.makeText(activity, activity.tr("Unable to open Android printing. Check that a print service is available."), android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
                web.loadDataWithBaseURL(null, document(sale, branding, paper, activity::tr), "text/html", "UTF-8", null)
            }.show()
    }
}

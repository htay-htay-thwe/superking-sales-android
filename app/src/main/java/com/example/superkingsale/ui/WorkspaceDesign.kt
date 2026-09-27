package com.example.superkingsale.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.HorizontalScrollView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.example.superkingsale.R
import com.google.android.material.button.MaterialButton

/** The desktop reference has a bounded application canvas, not edge-to-edge stretched forms. */
class WorkspaceColumn @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = context.resources.getDimensionPixelSize(R.dimen.foundation_content_max)
        super.onMeasure(MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(widthMeasureSpec), maxWidth), MeasureSpec.EXACTLY), heightMeasureSpec)
    }
}

/** Reflows existing children at measurement time: resizing never reconstructs or clears inputs. */
class ResponsiveGrid @JvmOverloads constructor(context: Context, private val minimumCellDp: Int = 260, private val maximumColumns: Int = 2,
    private val featuredFirst: Boolean = false, private val equalRowHeights: Boolean = false, private val gapDp: Int = 8,
    private val verticalGapDp: Int = gapDp) : ViewGroup(context) {
    private val positions = mutableListOf<android.graphics.Rect>()
    private val horizontalGap get() = context.dp(gapDp)
    private val verticalGap get() = context.dp(verticalGapDp)
    override fun onViewAdded(child: View) { super.onViewAdded(child); positions.add(android.graphics.Rect()) }
    override fun onViewRemoved(child: View) { super.onViewRemoved(child); if (positions.isNotEmpty()) positions.removeAt(positions.lastIndex) }
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        val scale = resources.configuration.fontScale.coerceAtLeast(1f)
        val columns = ((available + horizontalGap) / (context.dp(minimumCellDp).times(scale).toInt() + horizontalGap)).coerceIn(1, maximumColumns)
        val cellWidth = ((available - horizontalGap * (columns - 1)) / columns).coerceAtLeast(1)
        var y = paddingTop
        var rowHeight = 0
        var column = 0
        var rowStart = 0
        fun alignRow(end: Int) {
            if (!equalRowHeights) return
            for (index in rowStart..end) {
                val child = getChildAt(index)
                if (child.visibility == GONE) continue
                val rect = positions[index]
                child.measure(MeasureSpec.makeMeasureSpec(rect.width(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY))
                rect.bottom = rect.top + rowHeight
            }
        }
        for ((index, child) in children.withIndex()) {
            if (child.visibility == GONE) { positions[index].setEmpty(); continue }
            val full = featuredFirst && index == 0 && columns < maximumColumns
            val w = if (full) available else cellWidth
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val x = paddingLeft + column * (cellWidth + horizontalGap)
            positions[index].set(x, y, x + w, y + child.measuredHeight)
            rowHeight = maxOf(rowHeight, child.measuredHeight)
            column++
            if (full || column == columns) { alignRow(index); y += rowHeight + verticalGap; rowHeight = 0; column = 0; rowStart = index + 1 }
        }
        if (column > 0) alignRow(childCount - 1)
        val height = (if (column > 0) y + rowHeight else if (children.any { it.visibility != GONE }) y - verticalGap else paddingTop) + paddingBottom
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        children.forEachIndexed { i, view ->
            val rect = positions[i]
            if (layoutDirection == LAYOUT_DIRECTION_RTL) view.layout(width - rect.right, rect.top, width - rect.left, rect.bottom)
            else view.layout(rect.left, rect.top, rect.right, rect.bottom)
        }
    }
}

internal fun Context.ink(color: Int) = ContextCompat.getColor(this, color)
internal fun View.surface(tint: Boolean = false, border: Boolean = true) {
    background = GradientDrawable().apply {
        cornerRadius = context.dp(6).toFloat()
        setColor(context.ink(if (tint) R.color.workspace_tint else R.color.workspace_surface))
        if (border) setStroke(context.dp(1), context.ink(if (tint) R.color.workspace_accent else R.color.workspace_line))
    }
}
internal fun Context.column(padding: Int = 0) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    layoutParams = ViewGroup.LayoutParams(-1, -2)
}
/** Phone uses a bottom sheet; tablets use the same content as a centered, width-limited modal sheet. */
internal fun View.fitTabletBottomSheet(maxWidthDp: Int = 720) {
    if (resources.configuration.smallestScreenWidthDp < 600) return
    layoutParams = layoutParams.apply { width = context.dp(maxWidthDp).coerceAtMost(resources.displayMetrics.widthPixels) }
    (layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams)?.gravity = Gravity.CENTER_HORIZONTAL
    background = GradientDrawable().apply {
        cornerRadius = context.dp(16).toFloat()
        setColor(context.ink(R.color.workspace_surface))
    }
    clipToOutline = true
    outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
    elevation = context.dp(12).toFloat()
    requestLayout()
    post {
        val parentHeight = (parent as? View)?.height ?: resources.displayMetrics.heightPixels
        val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(this)
        behavior.isFitToContents = false
        behavior.expandedOffset = ((parentHeight - height) / 2).coerceAtLeast(0)
        behavior.skipCollapsed = true
        behavior.isHideable = false
        behavior.isDraggable = false
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }
}
internal fun LinearLayout.copyText(value: String, size: Float = 14f, bold: Boolean = false, color: Int = R.color.workspace_text): TextView = TextView(context).also {
    it.text = context.tr(value); it.textSize = size; it.setTextColor(context.ink(color))
    if (bold) it.setTypeface(it.typeface, Typeface.BOLD)
    it.setPadding(0, context.dp(1), 0, context.dp(1))
    addView(it, LinearLayout.LayoutParams(-1, -2))
}
internal fun LinearLayout.eyebrow(value: String) = copyText(value, 11f, true, R.color.workspace_primary)
internal fun MaterialButton.outlined() {
    backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_surface))
    setTextColor(context.ink(R.color.workspace_primary))
    strokeWidth = context.dp(1); strokeColor = ColorStateList.valueOf(context.ink(R.color.workspace_line))
}
internal fun LinearLayout.panel(title: String, overline: String = ""): LinearLayout {
    val panel = context.column(12).apply { surface() }
    addView(panel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(8); bottomMargin = context.dp(2) })
    if (overline.isNotEmpty()) panel.eyebrow(overline)
    if (title.isNotEmpty()) panel.copyText(title, 18f, true)
    return panel
}
internal fun LinearLayout.grid(minimum: Int = 260, columns: Int = 2, gapDp: Int = 8): ResponsiveGrid = ResponsiveGrid(context, minimum, columns, gapDp = gapDp).also {
    addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(6) })
}
internal fun ResponsiveGrid.cell(): LinearLayout = context.column().also { addView(it) }
internal fun LinearLayout.options(title: String, values: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    copyText(title, 13f, true)
    val group = grid(125, 3)
    values.forEach { (key, label) ->
        group.cell().button(label) { onSelect(key) }.apply {
            outlined(); isSelected = key == selected
            if (isSelected) {
                backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_tint))
                strokeColor = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
            }
            contentDescription = context.tr(label) + if (isSelected) ", " + context.tr("Selected") else ""
        }
    }
}
internal fun LinearLayout.stepper(step: Int, horizontalBleed: Int = 0) {
    val strip = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; setPadding(0, context.dp(6), 0, context.dp(6))
        setBackgroundColor(context.ink(R.color.workspace_background))
    }
    listOf("Information", "Products", "Quantity", "Review & submit").forEachIndexed { i, title ->
        val cell = context.column(4).apply { gravity = Gravity.CENTER }
        val active = i + 1 == step
        cell.copyText(if (i + 1 < step) "✓" else "${i + 1}", 14f, true,
            if (i + 1 <= step) R.color.workspace_surface else R.color.workspace_muted).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(context.dp(30), context.dp(30)).apply { gravity = Gravity.CENTER_HORIZONTAL }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(context.ink(if (i + 1 <= step) R.color.workspace_primary else R.color.workspace_surface))
                setStroke(context.dp(1), context.ink(if (i + 1 <= step) R.color.workspace_primary else R.color.workspace_line))
            }
        }
        cell.copyText(title, 11f, active, if (active) R.color.workspace_primary else R.color.workspace_muted).apply { gravity = Gravity.CENTER }
        cell.contentDescription = context.tr("Step ${i + 1} of 4") + ", " + context.tr(title)
        cell.isSelected = active
        strip.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
    }
    addView(strip, LinearLayout.LayoutParams(-1, -2).apply {
        marginStart = -horizontalBleed
        marginEnd = -horizontalBleed
    })
}

enum class CardKind { PANEL, HEADER, SALE_DETAIL_HEADER, SALE_DETAIL_TRANSACTION, SALE_DETAIL_TOTAL, STOCK_HEADER, HOME_HEADER, TRIP_HEADER, TRIP_HERO, TRIP_METRICS, STOCK_SUMMARY, CASH_BREAKDOWN, CASH_RETURN, CASH_LEDGER, PAYMENT_GROUP, PAYMENT_METHOD, ACTION_GRID, DANGER_PANEL, SECTION, ROW, PRODUCT_PICKER, SALE_PREVIEW, TABLE_ROW, SHIPMENT_ROW, SHIPMENT_TOTAL, PAGINATION, METRICS, GROUP, COLUMNS, CTA, NAV_CARD, EMPTY, CONTROLS }
data class Metric(val title: String, val value: String, val detail: String = "", val highlight: Boolean = false)

private fun statusColors(status: String): Pair<Int, Int> {
    val normalized = status.lowercase().replace('_', ' ')
    return when {
        normalized.startsWith("sr-") -> R.color.workspace_success to R.color.workspace_success_tint
        normalized in listOf("posted", "confirmed", "received", "active", "operation", "online", "available", "credit enabled", "no credit due") ->
            R.color.workspace_success to R.color.workspace_success_tint
        normalized in listOf("in transit", "informational") -> R.color.workspace_info to R.color.workspace_info_tint
        normalized in listOf("draft", "pending", "ending", "attention") -> R.color.workspace_warning to R.color.workspace_warning_tint
        normalized in listOf("failed", "voided", "blocked", "reversed") -> R.color.workspace_error to R.color.workspace_error_tint
        else -> R.color.workspace_muted to R.color.workspace_background
    }
}

internal fun LinearLayout.statusBadge(status: String): TextView {
    val colors = statusColors(status)
    return copyText("• " + status.replace('_', ' '), 11f, true, colors.first).apply {
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(4) }
        setPadding(context.dp(8), context.dp(3), context.dp(8), context.dp(3))
        background = GradientDrawable().apply {
            cornerRadius = context.dp(12).toFloat()
            setColor(context.ink(colors.second))
        }
    }
}

private fun rowIcon(item: Card): Int = when {
    item.key.startsWith("customer") -> R.drawable.ic_customer
    item.key.startsWith("sale") && item.detail.contains("credit", ignoreCase = true) -> R.drawable.ic_customer
    item.key.startsWith("sale") -> R.drawable.ic_cash
    item.key.startsWith("stock") || item.key.startsWith("item") || item.key.startsWith("product") -> R.drawable.ic_stock
    item.key.startsWith("receive") || item.key.startsWith("transfer") || item.key.startsWith("incoming") -> R.drawable.ic_trip
    item.key.startsWith("issue") -> R.drawable.ic_stock
    item.key.startsWith("cash") || item.key.startsWith("ledger") -> R.drawable.ic_cash
    else -> R.drawable.ic_sales
}

private fun actionIcon(key: String): Int = when (key) {
    "new_sale" -> R.drawable.ic_new_sale
    "stock" -> R.drawable.ic_stock
    "cash" -> R.drawable.ic_cash
    "expense" -> R.drawable.ic_sales
    else -> R.drawable.ic_chevron_right
}

/** Keeps icon-only actions optically centered in the same outlined square on every screen. */
private fun MaterialButton.centerIconOnly(icon: Int) {
    text = ""
    setIconResource(icon)
    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
    iconPadding = 0
    setPadding(0, 0, 0, 0)
    insetTop = 0
    insetBottom = 0
    minWidth = 0
    minimumWidth = 0
    gravity = Gravity.CENTER
}

/** Presentation only. All actions are sent back to the existing guarded fragment handlers. */
internal fun cardView(context: Context, item: Card, enabled: () -> Boolean, action: (CardAction) -> Unit,
    controls: ((LinearLayout, String) -> Unit)? = null, includeTrailingDivider: Boolean = true): View {
    val compact = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("density", "compact") == "compact"
    val root = context.column(if (item.kind in listOf(CardKind.METRICS, CardKind.COLUMNS)) 0 else if (compact) 8 else 12)
    if (item.kind == CardKind.GROUP && item.key == "customer-list") root.setPadding(0, context.dp(if (compact) 8 else 12), 0, 0)
    if (item.kind == CardKind.ROW) root.setPadding(context.dp(8), context.dp(4), context.dp(8), context.dp(4))
    if (item.kind in listOf(CardKind.TABLE_ROW, CardKind.SHIPMENT_ROW)) root.setPadding(context.dp(6), 0, context.dp(6), 0)
    if (item.kind == CardKind.SHIPMENT_TOTAL) {
        root.setPadding(context.dp(6), context.dp(4), context.dp(6), context.dp(4))
        root.setBackgroundColor(context.ink(R.color.workspace_background))
    }
    if (item.kind in listOf(CardKind.HEADER, CardKind.HOME_HEADER, CardKind.SECTION)) root.setPadding(0, context.dp(6), 0, context.dp(6))
    if (item.kind == CardKind.CONTROLS) root.setPadding(context.dp(10), context.dp(6), context.dp(10), context.dp(6))
    if (item.kind == CardKind.CONTROLS) { controls?.invoke(root, item.key); return root }
    if (item.kind == CardKind.SALE_DETAIL_HEADER) {
        root.setPadding(context.dp(2), context.dp(6), context.dp(2), context.dp(10))
        val back = TextView(context).apply {
            text = "‹  " + context.tr("Sales"); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.ink(R.color.workspace_muted)); setPadding(0, context.dp(4), 0, context.dp(8))
            isClickable = true; setOnClickListener { if (enabled()) action(CardAction("Sales", "sales")) }
        }
        root.addView(back, LinearLayout.LayoutParams(-1, -2))
        root.eyebrow(item.eyebrow)
        root.copyText(item.title, 22f, false)
        if (item.detail.isNotBlank()) root.copyText(item.detail, 15f, color = R.color.workspace_muted)
        val actionRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (item.status.isNotBlank()) actionRow.addView(context.column().apply { statusBadge(item.status) }, LinearLayout.LayoutParams(0, -2, 1f))
        item.actions.forEach { command ->
            actionRow.addView(MaterialButton(context).apply {
                text = context.tr(command.label); textSize = 13f; isAllCaps = false
                if (command.key == "print") setIconResource(R.drawable.ic_print)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; iconPadding = context.dp(8)
                setOnClickListener { if (enabled()) action(command) }
            }, LinearLayout.LayoutParams(-2, context.dp(48)).apply { marginStart = context.dp(8) })
        }
        root.addView(actionRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(14) })
        return root
    }
    if (item.kind == CardKind.SALE_DETAIL_TRANSACTION) {
        root.setPadding(0, 0, 0, 0)
        root.surface()
        context.column(10).apply {
            eyebrow(item.eyebrow)
            copyText(item.title, 17f, false)
        }.also { root.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        val grid = ResponsiveGrid(context, 150, 2, equalRowHeights = true, gapDp = 0)
        root.addView(grid, LinearLayout.LayoutParams(-1, -2))
        item.metrics.forEach { metric ->
            grid.cell().apply {
                setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
                copyText(metric.title, 10f, color = R.color.workspace_muted)
                copyText(metric.value, 12f, true)
                if (metric.detail.isNotBlank()) copyText(metric.detail, 9f, color = R.color.workspace_muted)
                background = GradientDrawable().apply {
                    setColor(context.ink(R.color.workspace_surface))
                    setStroke(context.dp(1), context.ink(R.color.workspace_line))
                }
            }
        }
        grid.cell().apply {
            setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
            copyText("Notes", 10f, color = R.color.workspace_muted)
            copyText(item.detail.ifBlank { "No notes" }, 12f, true)
            background = GradientDrawable().apply {
                setColor(context.ink(R.color.workspace_surface))
                setStroke(context.dp(1), context.ink(R.color.workspace_line))
            }
        }
        item.actions.firstOrNull()?.let { command ->
            TextView(context).apply {
                text = context.tr(command.label); textSize = 11f; setTextColor(context.ink(R.color.workspace_primary))
                paintFlags = paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                setPadding(context.dp(8), context.dp(18), context.dp(8), context.dp(15)); isClickable = true
                setOnClickListener { if (enabled()) action(command) }
            }.also { root.addView(it, LinearLayout.LayoutParams(-1, -2)) }
        }
        return root
    }
    if (item.kind == CardKind.SALE_DETAIL_TOTAL) {
        root.minimumWidth = context.dp(640)
        root.setPadding(context.dp(2), context.dp(3), context.dp(2), context.dp(3))
        root.setBackgroundColor(context.ink(R.color.workspace_background))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            minimumWidth = context.dp(640)
        }
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
        item.metrics.forEach { metric ->
            context.column().apply {
                setPadding(context.dp(2), context.dp(2), context.dp(2), context.dp(2))
                gravity = Gravity.END
                copyText(metric.title, 9f, color = R.color.workspace_muted).apply { gravity = Gravity.END }
                copyText(metric.value, 12f, true, if (metric.title.contains("total", true)) R.color.workspace_primary else R.color.workspace_text).apply { gravity = Gravity.END }
            }.also { row.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        }
        return root
    }
    if (item.kind == CardKind.PRODUCT_PICKER) {
        root.setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
        root.background = GradientDrawable().apply {
            cornerRadius = context.dp(7).toFloat()
            setColor(context.ink(if (item.featured) R.color.workspace_tint else R.color.workspace_surface))
            setStroke(context.dp(1), context.ink(if (item.featured) R.color.workspace_accent else R.color.workspace_line))
        }
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(context).apply {
            text = if (item.featured) "✓" else ""
            textSize = 15f; gravity = Gravity.CENTER; setTextColor(context.ink(R.color.workspace_accent))
            background = GradientDrawable().apply {
                cornerRadius = context.dp(5).toFloat()
                setColor(context.ink(if (item.featured) R.color.workspace_tint else R.color.workspace_surface))
                setStroke(context.dp(1), context.ink(if (item.featured) R.color.workspace_accent else R.color.workspace_muted))
            }
        }, LinearLayout.LayoutParams(context.dp(24), context.dp(24)).apply { marginEnd = context.dp(10) })
        top.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_stock); imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
            setPadding(context.dp(9), context.dp(9), context.dp(9), context.dp(9))
            background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_tint)) }
        }, LinearLayout.LayoutParams(context.dp(44), context.dp(44)).apply { marginEnd = context.dp(10) })
        top.addView(context.column().apply {
            copyText(item.title, 14f, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
            copyText(item.detail, 11f, color = R.color.workspace_muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top, LinearLayout.LayoutParams(-1, -2))
        val bottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bottom.setPadding(context.dp(34), context.dp(6), 0, 0)
        item.metrics.forEach { metric ->
            context.column().apply {
                copyText(metric.value, 14f, true)
                copyText(metric.detail, 10f, color = R.color.workspace_muted)
            }.also { bottom.addView(it, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = context.dp(12) }) }
        }
        context.column().apply {
            gravity = Gravity.END
            copyText(item.value, 14f, true).apply { gravity = Gravity.END }
            copyText("MDY-1 Way price", 10f, color = R.color.workspace_muted).apply { gravity = Gravity.END }
        }.also { bottom.addView(it, LinearLayout.LayoutParams(0, -2, 2.2f)) }
        root.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        root.contentDescription = listOf(item.title, item.detail, item.value).joinToString(". ")
        root.isFocusable = true
        root.isClickable = true
        root.setOnClickListener { if (enabled()) item.actions.firstOrNull()?.let(action) }
        return root
    }
    if (item.kind == CardKind.HOME_HEADER) {
        if (item.detail.isNotBlank()) root.copyText(item.detail, 13f, color = R.color.workspace_muted)
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(context).apply {
            text = context.tr(item.title); textSize = 18f; setTypeface(typeface, Typeface.NORMAL); setTextColor(context.ink(R.color.workspace_text))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (item.status.isNotBlank()) row.addView(context.column().apply { gravity = Gravity.END; statusBadge(item.status) }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(8) })
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
        return root
    }
    if (item.kind == CardKind.STOCK_HEADER) {
        root.setPadding(0, context.dp(6), 0, context.dp(4))
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val copy = context.column().apply {
            copyText(item.eyebrow, 10f, color = R.color.workspace_muted)
            copyText(item.title, 18f).apply { setPadding(0, context.dp(2), 0, 0) }
        }
        top.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(context.column().apply { gravity = Gravity.END; statusBadge(item.status) }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(8) })
        root.addView(top, LinearLayout.LayoutParams(-1, -2))
        return root
    }
    if (item.kind == CardKind.TRIP_HEADER) {
        root.setPadding(0, context.dp(6), 0, context.dp(6))
        if (item.eyebrow.isNotBlank()) root.eyebrow(item.eyebrow)
        root.copyText(item.title, 18f)
        if (item.detail.isNotBlank()) root.copyText(item.detail, 11f, color = R.color.workspace_muted).apply { setPadding(0, context.dp(2), 0, 0) }
        return root
    }
    if (item.kind == CardKind.TRIP_HERO) {
        val heroPadding = context.dp(if (resourcesWide(context)) 16 else 14)
        root.setPadding(heroPadding, context.dp(14), heroPadding, context.dp(14))
        val base = GradientDrawable().apply {
            cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_surface))
            setStroke(context.dp(1), context.ink(R.color.workspace_line))
        }
        val accent = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_accent)) }
        root.background = LayerDrawable(arrayOf(base, accent)).apply {
            setLayerWidth(1, context.dp(4)); setLayerGravity(1, Gravity.START or Gravity.FILL_VERTICAL)
        }
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        if (item.key == "active-trip") line.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_trip)
            imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
            setPadding(context.dp(7), context.dp(7), context.dp(7), context.dp(7))
            background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_tint)) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(36), context.dp(36)).apply { marginEnd = context.dp(10) })
        val identity = context.column().apply {
            copyText(item.title, 13f, color = R.color.workspace_muted)
            copyText(item.value, 18f).apply { setPadding(0, context.dp(4), 0, 0) }
            item.detail.lines().filter { it.isNotBlank() }.forEach { copyText(it, 12f, color = R.color.workspace_muted).apply { setPadding(0, context.dp(4), 0, 0) } }
        }
        line.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        if (item.status.isNotBlank()) line.addView(context.column().apply { gravity = Gravity.END; statusBadge(item.status) }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(8) })
        root.addView(line, LinearLayout.LayoutParams(-1, -2))
        return root
    }
    if (item.kind == CardKind.CASH_BREAKDOWN) {
        root.setPadding(0, 0, 0, 0)
        root.surface()
        item.metrics.forEachIndexed { index, metric ->
            val cell = context.column(10).apply {
                copyText(metric.title, 11f, color = R.color.workspace_muted)
                copyText(metric.value, 13f, true)
            }
            root.addView(cell, LinearLayout.LayoutParams(-1, -2))
            if (index < item.metrics.lastIndex) root.addView(View(context).apply {
                setBackgroundColor(context.ink(R.color.workspace_line))
            }, LinearLayout.LayoutParams(-1, context.dp(1)))
        }
        return root
    }
    if (item.kind == CardKind.TRIP_METRICS) {
        root.setPadding(0, 0, 0, 0)
        val grid = ResponsiveGrid(context, 150, 2, equalRowHeights = true)
        root.addView(grid, LinearLayout.LayoutParams(-1, -2))
        item.metrics.forEach { metric ->
            val cell = context.column(if (compact) 10 else 12).apply { surface() }
            cell.copyText(metric.title, 12f, true, R.color.workspace_muted)
            cell.copyText(metric.value, 18f, true).apply { setPadding(0, context.dp(4), 0, 0) }
            cell.copyText(metric.detail, 11f, color = R.color.workspace_muted).apply { setPadding(0, context.dp(2), 0, 0) }
            grid.addView(cell)
        }
        return root
    }
    if (item.kind == CardKind.STOCK_SUMMARY) {
        root.surface(item.featured)
        val metric = item.metrics.first()
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(context).apply {
            text = context.tr(metric.title); textSize = 11f; setTypeface(typeface, Typeface.BOLD); setTextColor(context.ink(R.color.workspace_muted))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(ImageView(context).apply {
            setImageResource(if (metric.title.contains("Incoming", true)) R.drawable.ic_trip else R.drawable.ic_stock)
            imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent)); setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
            background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_tint)) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(30), context.dp(30)))
        root.addView(top, LinearLayout.LayoutParams(-1, -2))
        root.copyText(metric.value, 20f, true).apply { setPadding(0, context.dp(4), 0, 0) }
        root.copyText(metric.detail, 11f, color = R.color.workspace_muted).apply { setPadding(0, context.dp(2), 0, 0) }
        root.minimumHeight = context.dp(102)
        return root
    }
    if (item.kind == CardKind.PAYMENT_GROUP) {
        root.surface()
        val headingRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = context.column().apply { eyebrow(item.eyebrow); copyText(item.title, 15f) }
        headingRow.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        headingRow.addView(TextView(context).apply {
            text = context.tr(item.detail); textSize = 11f; setTextColor(context.ink(R.color.workspace_muted)); maxLines = 3
        }, LinearLayout.LayoutParams(0, -2, 1.15f).apply { marginStart = context.dp(8) })
        root.addView(headingRow, LinearLayout.LayoutParams(-1, -2))
        item.children.forEachIndexed { index, child ->
            root.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)).apply { topMargin = if (index == 0) context.dp(8) else 0 })
            root.addView(cardView(context, child, enabled, action, controls, includeTrailingDivider = false))
        }
        return root
    }
    if (item.kind == CardKind.PAYMENT_METHOD) {
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(ImageView(context).apply {
            setImageResource(if (item.title.contains("cash", true)) R.drawable.ic_cash else R.drawable.ic_sales)
            imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent)); setPadding(context.dp(7), context.dp(7), context.dp(7), context.dp(7))
            background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_tint)) }
        }, LinearLayout.LayoutParams(context.dp(32), context.dp(32)).apply { marginEnd = context.dp(8) })
        val content = context.column().apply {
            copyText(item.title, 14f, true)
            copyText(item.detail, 10f, color = R.color.workspace_muted)
            val totals = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            item.metrics.forEach { metric -> totals.addView(context.column().apply {
                copyText(metric.title, 9f, color = R.color.workspace_muted); copyText(metric.value, 11f, true)
            }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = context.dp(18) }) }
            addView(totals, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(5) })
        }
        line.addView(content, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(TextView(context).apply { text = item.value; textSize = 13f; setTypeface(typeface, Typeface.BOLD); setTextColor(context.ink(R.color.workspace_text)); gravity = Gravity.END }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(8) })
        root.addView(line, LinearLayout.LayoutParams(-1, -2))
        return root
    }
    if (item.kind == CardKind.ACTION_GRID) {
        root.setPadding(0, 0, 0, 0)
        val grid = ResponsiveGrid(context, 145, 2, equalRowHeights = true, gapDp = 8, verticalGapDp = 0)
        root.addView(grid, LinearLayout.LayoutParams(-1, -2))
        item.actions.forEachIndexed { index, cardAction ->
            val cell = context.column(0)
            cell.button(cardAction.label, enabled()) { if (enabled()) action(cardAction) }.apply {
                setIconResource(actionIcon(cardAction.key)); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; iconPadding = context.dp(8)
                minHeight = context.dp(48); textSize = 12f; setPadding(context.dp(8), 0, context.dp(8), 0)
                if (index == 0) {
                    backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent)); setTextColor(context.ink(R.color.workspace_surface)); iconTint = ColorStateList.valueOf(context.ink(R.color.workspace_surface))
                } else { outlined(); iconTint = ColorStateList.valueOf(context.ink(R.color.workspace_text)); setTextColor(context.ink(R.color.workspace_text)) }
            }
            grid.addView(cell)
        }
        return root
    }
    if (item.kind == CardKind.DANGER_PANEL) {
        root.background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_error_tint)); setStroke(context.dp(1), context.ink(R.color.workspace_error)) }
        root.copyText(item.title, 14f, true)
        if (item.detail.isNotBlank()) root.copyText(item.detail, 12f, color = R.color.workspace_muted).apply { setPadding(0, context.dp(4), 0, context.dp(8)) }
        item.actions.forEach { cardAction -> root.button(cardAction.label, enabled()) { if (enabled()) action(cardAction) }.apply {
            backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_error)); setTextColor(context.ink(R.color.workspace_surface)); minHeight = context.dp(48)
        } }
        return root
    }
    if (item.kind == CardKind.EMPTY) {
        root.gravity = Gravity.CENTER; root.setPadding(context.dp(12), context.dp(24), context.dp(12), context.dp(24))
        root.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_stock); imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent)); setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(context.ink(R.color.workspace_tint)) }
        }, LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = context.dp(8) })
        root.copyText(item.title, 14f, true).apply { gravity = Gravity.CENTER }
        if (item.detail.isNotBlank()) root.copyText(item.detail, 12f, color = R.color.workspace_muted).apply { gravity = Gravity.CENTER; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, context.dp(4), 0, 0) }
        return root
    }
    if (item.kind == CardKind.COLUMNS) {
        val grid = root.grid(410, 2)
        item.children.forEach { grid.addView(cardView(context, it, enabled, action, controls)) }
        return root
    }
    if (item.kind == CardKind.METRICS) {
        val grid = ResponsiveGrid(context, 150, item.metrics.size.coerceIn(2, 4), item.featured, equalRowHeights = true)
        root.addView(grid, LinearLayout.LayoutParams(-1, -2))
        item.metrics.forEach { m ->
            val cell = context.column(if (compact) 10 else 12).apply { surface(m.highlight) }
            val label = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            label.addView(TextView(context).apply {
                text = context.tr(m.title); textSize = if (item.featured) 11f else 12f; setTypeface(typeface, Typeface.BOLD); setTextColor(context.ink(R.color.workspace_muted))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            label.addView(ImageView(context).apply {
                setImageResource(when {
                    m.title.contains("sale", true) -> R.drawable.ic_sales
                    m.title.contains("hold", true) -> R.drawable.ic_cash
                    m.title.contains("return", true) -> R.drawable.ic_sales
                    else -> R.drawable.ic_stock
                })
                imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent)); setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
                background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_tint)) }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(context.dp(30), context.dp(30)))
            cell.addView(label, LinearLayout.LayoutParams(-1, -2))
            cell.copyText(m.value.removeSuffix(" MMK"), 18f, true, if (m.highlight) R.color.workspace_primary else R.color.workspace_text)
            val detail = if (m.detail.isNotBlank()) m.detail else if (!item.featured && m.value.endsWith(" MMK")) "MMK" else ""
            if (detail.isNotBlank()) cell.copyText(detail, if (item.featured) 10f else 12f, false, R.color.workspace_muted)
            grid.addView(cell)
        }
        return root
    }
    if (item.kind == CardKind.CTA) {
        root.background = GradientDrawable().apply { cornerRadius = context.dp(8).toFloat(); setColor(context.ink(R.color.workspace_accent)) }
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(context).apply {
            text = "+"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(context.ink(R.color.workspace_surface))
            background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(android.graphics.Color.TRANSPARENT); setStroke(context.dp(1), context.ink(R.color.workspace_surface)) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(36), context.dp(36)))
        val copy = context.column().apply { copyText(item.title, 13f, true, R.color.workspace_surface); copyText(item.detail, 10f, color = R.color.workspace_surface) }
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(8) })
        row.addView(ImageView(context).apply { setImageResource(R.drawable.ic_chevron_right); imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_surface)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(context.dp(24), context.dp(24)))
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
        root.minimumHeight = context.dp(58)
        root.isFocusable = true
        root.contentDescription = context.tr(item.title) + ". " + context.tr(item.detail)
        root.setOnClickListener { if (enabled()) item.actions.firstOrNull()?.let(action) }
        return root
    }
    if (item.kind == CardKind.SALE_PREVIEW) {
        val wide = resourcesWide(context)
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(ImageView(context).apply {
            setImageResource(rowIcon(item)); imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent)); setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
            background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_tint)); setStroke(context.dp(1), context.ink(R.color.workspace_accent)) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(36), context.dp(36)).apply { marginEnd = context.dp(10) })
        val identity = context.column().apply {
            copyText(item.title, 14f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
            copyText(item.detail, 11f, color = R.color.workspace_muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        }
        top.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
        if (item.status.isNotBlank()) top.addView(context.column().apply { statusBadge(item.status) }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(6) })
        item.actions.firstOrNull { it.key == "print" }?.let { print ->
            top.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
                centerIconOnly(R.drawable.ic_print); iconTint = ColorStateList.valueOf(context.ink(R.color.workspace_text))
                backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_surface)); strokeWidth = context.dp(1)
                strokeColor = ColorStateList.valueOf(context.ink(R.color.workspace_line)); cornerRadius = context.dp(5)
                contentDescription = context.tr(print.label)
                setOnClickListener { if (enabled()) action(print) }
            }, LinearLayout.LayoutParams(context.dp(if (wide) 46 else 40), context.dp(if (wide) 46 else 40)).apply { marginStart = context.dp(6) })
        }
        val navigation = item.actions.firstOrNull { it.key == "sale_detail" }
        navigation?.let { details ->
            top.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
                centerIconOnly(R.drawable.ic_chevron_right); iconTint = ColorStateList.valueOf(context.ink(R.color.workspace_text))
                backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_surface)); strokeWidth = context.dp(1)
                strokeColor = ColorStateList.valueOf(context.ink(R.color.workspace_line)); cornerRadius = context.dp(5)
                contentDescription = context.tr(details.label)
                setOnClickListener { if (enabled()) action(details) }
            }, LinearLayout.LayoutParams(context.dp(if (wide) 46 else 40), context.dp(if (wide) 46 else 40)).apply { marginStart = context.dp(6) })
        }
        root.addView(top, LinearLayout.LayoutParams(-1, -2))
        root.copyText(item.value, if (wide) 16f else 14f, true).apply { setPadding(0, context.dp(6), 0, 0) }
        if (item.eyebrow.isNotBlank()) root.copyText(item.eyebrow, 11f, color = R.color.workspace_muted)
        if (navigation != null) { root.isFocusable = true; root.setOnClickListener { if (enabled()) action(navigation) } }
        root.contentDescription = listOf(item.title, item.detail, item.status, item.value, item.eyebrow).filter { it.isNotBlank() }.joinToString(". ")
        if (includeTrailingDivider) root.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)).apply { topMargin = context.dp(8) })
        return root
    }
    if (item.kind == CardKind.NAV_CARD) {
        root.surface(tint = true)
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(ImageView(context).apply { setImageResource(R.drawable.ic_sales); imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply { marginEnd = context.dp(10) })
        val copy = context.column().apply { copyText(item.title, 13f, true); copyText(item.detail, 10f, color = R.color.workspace_muted) }
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ImageView(context).apply { setImageResource(R.drawable.ic_chevron_right); imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_text)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(context.dp(24), context.dp(24)))
        root.addView(row, LinearLayout.LayoutParams(-1, -2)); root.minimumHeight = context.dp(64); root.isFocusable = true
        root.setOnClickListener { if (enabled()) item.actions.firstOrNull()?.let(action) }
        root.contentDescription = context.tr(item.title) + ". " + context.tr(item.detail)
        return root
    }
    if (item.kind in listOf(CardKind.TABLE_ROW, CardKind.SHIPMENT_ROW, CardKind.SHIPMENT_TOTAL)) {
        val shipment = item.kind in listOf(CardKind.SHIPMENT_ROW, CardKind.SHIPMENT_TOTAL)
        val wide = resourcesWide(context)
        val line = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(if (item.kind == CardKind.SHIPMENT_TOTAL) 44 else if (shipment && wide) 64 else if (shipment) 58 else 48)
        }
        val main = context.column().apply {
            copyText(item.title, if (shipment && wide) 14f else 12f, item.kind != CardKind.SHIPMENT_TOTAL).apply { maxLines = if (shipment) 3 else 2; ellipsize = android.text.TextUtils.TruncateAt.END }
            if (item.detail.isNotBlank()) copyText(item.detail.trim(), if (shipment && wide) 11f else 10f, color = R.color.workspace_muted).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        }
        line.addView(main, LinearLayout.LayoutParams(0, -2, if (shipment) (if (wide) 2.8f else 2.6f) else 1.55f))
        item.metrics.forEach { metric ->
            val cell = context.column().apply {
                gravity = Gravity.END
                copyText(metric.value, if (shipment && wide) 14f else 12f, true).apply { gravity = Gravity.END }
                if (metric.detail.isNotBlank()) copyText(metric.detail, if (shipment && wide) 10f else 9f, color = R.color.workspace_muted).apply { gravity = Gravity.END }
            }
            line.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(4) })
        }
        root.addView(line, LinearLayout.LayoutParams(-1, -2))
        root.contentDescription = (listOf(item.title, item.detail) + item.metrics.flatMap { listOf(it.title, it.value, it.detail) }).filter { it.isNotBlank() }.joinToString(". ")
        root.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)))
        return root
    }
    if (item.kind == CardKind.PAGINATION) {
        root.setPadding(context.dp(2), context.dp(8), context.dp(2), context.dp(4))
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(TextView(context).apply { text = item.detail; textSize = 10f; setTextColor(context.ink(R.color.workspace_muted)) }, LinearLayout.LayoutParams(0, -2, 1f))
        val previous = item.actions.firstOrNull { it.key == "previous" }
        line.addView(TextView(context).apply {
            text = context.tr("Previous"); textSize = 10f; setTextColor(context.ink(R.color.workspace_muted)); alpha = if (previous == null) .55f else 1f
            setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8)); previous?.let { command -> setOnClickListener { if (enabled()) action(command) } }
        })
        line.addView(TextView(context).apply {
            text = item.value; textSize = 10f; setTypeface(typeface, Typeface.BOLD); setTextColor(context.ink(R.color.workspace_primary));
            setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
        })
        val next = item.actions.firstOrNull { it.key == "next" }
        line.addView(TextView(context).apply {
            text = context.tr("Next"); textSize = 10f; setTextColor(context.ink(R.color.workspace_muted)); alpha = if (next == null) .55f else 1f
            setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8)); next?.let { command -> setOnClickListener { if (enabled()) action(command) } }
        })
        root.addView(line, LinearLayout.LayoutParams(-1, -2))
        return root
    }
    if (item.kind == CardKind.CASH_RETURN) {
        root.setPadding(context.dp(6), context.dp(8), context.dp(6), context.dp(8))
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_cash); imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
            setPadding(context.dp(7), context.dp(7), context.dp(7), context.dp(7))
            background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_tint)); setStroke(context.dp(1), context.ink(R.color.workspace_accent)) }
        }, LinearLayout.LayoutParams(context.dp(34), context.dp(34)).apply { marginEnd = context.dp(10) })
        top.addView(context.column().apply {
            copyText(item.title, 14f, true)
            copyText(item.detail, 11f, color = R.color.workspace_muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(top)
        root.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)).apply { topMargin = context.dp(8); bottomMargin = context.dp(7) })
        val bottom = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        bottom.addView(context.column().apply { statusBadge(item.status) }, LinearLayout.LayoutParams(0, -2, 1f))
        bottom.addView(TextView(context).apply {
            text = item.value; textSize = 13f; setTypeface(typeface, Typeface.BOLD); setTextColor(context.ink(R.color.workspace_text)); gravity = Gravity.END
        })
        root.addView(bottom)
        // Keep the established Cancel handover control unchanged.
        item.actions.forEach { command -> root.button(command.label, enabled()) { if (enabled()) action(command) }.outlined() }
        if (includeTrailingDivider) root.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)).apply { topMargin = context.dp(8) })
        return root
    }
    if (item.kind == CardKind.CASH_LEDGER) {
        root.setPadding(context.dp(6), context.dp(8), context.dp(6), context.dp(8))
        val negative = item.value.trim().startsWith("-")
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
        line.addView(TextView(context).apply {
            text = if (negative) "−" else "+"; gravity = Gravity.CENTER; textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.ink(if (negative) R.color.workspace_error else R.color.workspace_success))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(context.ink(if (negative) R.color.workspace_error_tint else R.color.workspace_success_tint)) }
        }, LinearLayout.LayoutParams(context.dp(30), context.dp(30)).apply { marginEnd = context.dp(10) })
        line.addView(context.column().apply {
            copyText(item.title, 14f, true)
            copyText(item.detail, 11f, color = R.color.workspace_muted).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
            copyText(if (!negative && !item.value.trim().startsWith("+")) "+${item.value}" else item.value, 13f, true,
                if (negative) R.color.workspace_error else R.color.workspace_success).apply { setPadding(0, context.dp(5), 0, 0) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(line)
        if (includeTrailingDivider) root.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)).apply { topMargin = context.dp(8) })
        return root
    }
    if (item.kind == CardKind.ROW) {
        if (item.key.startsWith("customer")) {
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP }
            line.addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_customer)
                imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
                setPadding(context.dp(7), context.dp(7), context.dp(7), context.dp(7))
                background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_tint)) }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply { marginEnd = context.dp(10) })
            val details = item.detail.lines().filter { it.isNotBlank() }
            val main = context.column()
            main.copyText(item.title, 14f, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
            details.take(2).forEachIndexed { index, value -> main.copyText(value, 11f, index == 1, R.color.workspace_muted) }
            main.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)).apply {
                topMargin = context.dp(6); bottomMargin = context.dp(5)
            })
            main.copyText(item.value.ifBlank { "No phone" }, 13f, true)
            details.drop(2).forEach { main.copyText(it, 11f, color = R.color.workspace_muted) }
            if (item.status.isNotBlank()) main.addView(context.column().apply {
                gravity = Gravity.END; statusBadge(item.status)
            }, LinearLayout.LayoutParams(-1, -2))
            line.addView(main, LinearLayout.LayoutParams(0, -2, 1f))
            root.addView(line, LinearLayout.LayoutParams(-1, -2))
            if (includeTrailingDivider) root.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)).apply { topMargin = context.dp(8) })
            return root
        }
        val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        line.addView(ImageView(context).apply {
            setImageResource(rowIcon(item)); imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
            setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
            background = GradientDrawable().apply { cornerRadius = context.dp(6).toFloat(); setColor(context.ink(R.color.workspace_tint)) }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(context.dp(32), context.dp(32)).apply { marginEnd = context.dp(8) })
        val main = context.column()
        main.copyText(item.title, 14f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        if (item.detail.isNotBlank()) main.copyText(item.detail.trim(), 12f, color = R.color.workspace_muted).apply {
            maxLines = if (item.key.startsWith("expense")) 1 else 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        line.addView(main, LinearLayout.LayoutParams(0, -2, 1f))
        if (item.value.isNotBlank() || item.status.isNotBlank()) {
            val side = context.column().apply { setPadding(context.dp(8), 0, 0, 0) }
            if (item.value.isNotBlank()) side.copyText(item.value, 14f, true).apply { gravity = Gravity.END }
            if (item.status.isNotBlank()) side.statusBadge(item.status).apply { gravity = Gravity.END }
            line.addView(side, LinearLayout.LayoutParams(context.dp(if (resourcesWide(context)) 190 else 120), -2))
        }
        root.addView(line, LinearLayout.LayoutParams(-1, -2))
        if (item.metrics.isNotEmpty()) {
            val grid = root.grid(130, 4)
            item.metrics.forEach { m -> grid.cell().apply { copyText(m.title, 11f, color = R.color.workspace_muted); copyText(m.value, 14f, true) } }
        }
        val navigation = item.actions.firstOrNull { it.key in listOf("sale_detail", "receiving") }
        val inlineAction = if (navigation != null) item.actions.firstOrNull { it != navigation && it.key == "print" } else null
        if (navigation != null) {
            root.isFocusable = true; root.minimumHeight = context.dp(56)
            root.contentDescription = listOf(item.title, item.value, item.detail, item.status, context.tr(navigation.label)).filter { it.isNotBlank() }.joinToString(". ")
            root.setOnClickListener { if (enabled()) action(navigation) }
            inlineAction?.let { print ->
                line.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
                    centerIconOnly(R.drawable.ic_print); iconTint = ColorStateList.valueOf(context.ink(R.color.workspace_text))
                    contentDescription = context.tr(print.label)
                    setOnClickListener { if (enabled()) action(print) }
                }, LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply { marginStart = context.dp(4) })
            }
            line.addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_right); imageTintList = ColorStateList.valueOf(context.ink(R.color.workspace_muted)); setPadding(context.dp(6), context.dp(10), context.dp(6), context.dp(10))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(context.dp(20), context.dp(40)))
            val selectable = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
            root.foreground = ContextCompat.getDrawable(context, selectable.resourceId)
        }
        val actions = item.actions.filter { it != navigation && it != inlineAction }
        if (actions.isNotEmpty()) {
            val buttons = root.grid(140, 3)
            actions.take(if (actions.size > 2) 1 else 2).forEach { a -> buttons.cell().button(a.label, enabled()) { if (enabled()) action(a) }.outlined() }
            if (actions.size > 2) {
                val more = buttons.cell().button("More actions", enabled()) {}.apply { outlined() }
                more.setOnClickListener {
                    if (enabled()) androidx.appcompat.widget.PopupMenu(context, more).apply {
                        actions.drop(1).forEach { a -> menu.add(context.tr(a.label)).setOnMenuItemClickListener { if (enabled()) action(a); true } }
                    }.show()
                }
            }
        }
        if (includeTrailingDivider) root.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)).apply { topMargin = context.dp(8) })
        return root
    }
    if (item.kind !in listOf(CardKind.HEADER, CardKind.SECTION, CardKind.ROW)) root.surface(item.kind == CardKind.CTA)
    val splitHeading = (item.key == "sales-activity" || item.actions.size == 1 || (item.actions.isEmpty() && item.status.isNotBlank())) &&
        item.kind in listOf(CardKind.HEADER, CardKind.SECTION, CardKind.GROUP)
    val heading = if (splitHeading) {
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        if (item.key == "customer-list") row.setPadding(context.dp(12), 0, context.dp(12), 0)
        val left = context.column()
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        val right = if (item.key in listOf("stock-receivings", "sales-activity")) LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        } else context.column().apply { gravity = Gravity.END }
        if (item.key == "stock-receivings" && item.status.isNotBlank()) {
            right.addView(context.column().apply { statusBadge(item.status) }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = context.dp(8) })
        }
        item.actions.forEach { a -> if (item.key == "stock-receivings") {
            right.addView(TextView(context).apply {
                text = context.tr(a.label); textSize = 10f; setTypeface(typeface, Typeface.BOLD); setTextColor(context.ink(R.color.workspace_accent))
                isSingleLine = true; gravity = Gravity.CENTER; minHeight = context.dp(32); setPadding(context.dp(2), 0, context.dp(2), 0)
                setOnClickListener { if (enabled()) action(a) }
            }, LinearLayout.LayoutParams(-2, context.dp(32)))
        } else right.button(a.label, enabled()) { if (enabled()) action(a) }.apply {
            outlined(); textSize = 12f; setPadding(context.dp(8), 0, context.dp(8), 0)
            if (item.key == "customers-header") {
                setIconResource(R.drawable.ic_new_sale); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = context.dp(6); iconTint = ColorStateList.valueOf(context.ink(R.color.workspace_surface))
                backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
                setTextColor(context.ink(R.color.workspace_surface)); strokeWidth = 0
                minHeight = context.dp(48); minimumHeight = context.dp(48)
            }
            if (item.key == "cash-header") {
                text = ""; setIconResource(R.drawable.ic_new_sale)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; iconPadding = 0
                iconTint = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
                setPadding(0, 0, 0, 0); minWidth = context.dp(48); minimumWidth = context.dp(48)
                layoutParams = LinearLayout.LayoutParams(context.dp(48), context.dp(48))
            }
            if (item.key == "sales-activity") {
                minHeight = context.dp(40); minimumHeight = context.dp(40)
                if (a.key == "sales_filters") {
                    text = ""; setIconResource(R.drawable.ic_filter)
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; iconPadding = 0
                    iconTint = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
                    setPadding(0, 0, 0, 0); minWidth = context.dp(40); minimumWidth = context.dp(40)
                    layoutParams = LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply { marginStart = context.dp(6) }
                } else {
                    setIconResource(R.drawable.ic_new_sale); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    iconPadding = context.dp(6); iconTint = ColorStateList.valueOf(context.ink(R.color.workspace_surface))
                    backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
                    setTextColor(context.ink(R.color.workspace_surface)); strokeWidth = 0
                }
            }
            if (item.key in listOf("recent-sales", "stock-preview")) {
                strokeWidth = 0; minWidth = 0; minimumWidth = 0; textSize = 10f
                setPadding(context.dp(4), 0, context.dp(4), 0)
                isSingleLine = true
                backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_surface))
            }
        } }
        if (item.status.isNotBlank() && item.key != "stock-receivings") right.statusBadge(item.status)
        row.addView(right, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(8) })
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
        left
    } else root
    if (item.eyebrow.isNotBlank()) heading.eyebrow(item.eyebrow)
    if (item.title.isNotBlank()) heading.copyText(item.title, when (item.kind) {
        CardKind.HEADER -> 22f
        CardKind.ROW -> 14f
        else -> 17f
    }, true)
    if (item.status.isNotBlank() && !splitHeading) heading.statusBadge(item.status)
    if (item.value.isNotBlank()) heading.copyText(item.value, if (item.kind == CardKind.ROW) 16f else 22f, true, R.color.workspace_primary)
    if (item.detail.isNotBlank()) root.copyText(item.detail.trim(), 13f, false, R.color.workspace_muted)
    if (item.metrics.isNotEmpty()) {
        val grid = root.grid(160, 4)
        item.metrics.forEach { m -> grid.cell().apply {
            copyText(m.title, 12f, false, R.color.workspace_muted); copyText(m.value, 14f, true)
            if (m.detail.isNotBlank()) copyText(m.detail, 12f, false, R.color.workspace_muted)
        } }
    }
    if (item.actions.isNotEmpty() && !splitHeading) {
        val buttons = root.grid(150, if (item.kind == CardKind.HEADER) 3 else 4)
        item.actions.forEach { a ->
            buttons.cell().button(a.label, enabled()) { if (enabled()) action(a) }.apply {
                if (item.kind != CardKind.CTA) outlined()
                if (a.key in listOf("end", "delete", "cancelCash")) setTextColor(com.google.android.material.color.MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError))
            }
        }
    }
    fun addTableHeader(first: Card) {
        val shipment = first.kind in listOf(CardKind.SHIPMENT_ROW, CardKind.SHIPMENT_TOTAL)
        val productWeight = if (shipment) (if (resourcesWide(context)) 2.8f else 2.6f) else 1.55f
        val tableHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
            setBackgroundColor(context.ink(R.color.workspace_background))
        }
        tableHeader.addView(TextView(context).apply { text = context.tr("Product").uppercase(); textSize = 10f; setTypeface(typeface, Typeface.BOLD); setTextColor(context.ink(R.color.workspace_muted)) }, LinearLayout.LayoutParams(0, -2, productWeight))
        first.metrics.forEach { metric ->
            tableHeader.addView(TextView(context).apply { text = context.tr(metric.title).uppercase(); textSize = 10f; gravity = Gravity.END; setTypeface(typeface, Typeface.BOLD); setTextColor(context.ink(R.color.workspace_muted)) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(4) })
        }
        root.addView(tableHeader, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(6) })
    }
    val tableKinds = listOf(CardKind.TABLE_ROW, CardKind.SHIPMENT_ROW, CardKind.SHIPMENT_TOTAL)
    val firstTable = item.children.firstOrNull { it.kind in tableKinds }
    val tableAfterControls = item.children.firstOrNull()?.kind == CardKind.CONTROLS
    val tableContent = if (item.key == "products") LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        minimumWidth = context.dp(640)
    } else null
    val tableTarget: ViewGroup = tableContent ?: root
    if (tableContent != null) {
        root.addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(tableContent)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(6) })
    }
    if (!tableAfterControls) firstTable?.let {
        if (tableContent == null) addTableHeader(it) else {
            val shipment = it.kind in listOf(CardKind.SHIPMENT_ROW, CardKind.SHIPMENT_TOTAL)
            val productWeight = if (shipment) (if (resourcesWide(context)) 2.8f else 2.6f) else 1.55f
            val tableHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
                setBackgroundColor(context.ink(R.color.workspace_background))
            }
            tableHeader.addView(TextView(context).apply { text = context.tr("Product").uppercase(); textSize = 10f; setTypeface(typeface, Typeface.BOLD); setTextColor(context.ink(R.color.workspace_muted)) }, LinearLayout.LayoutParams(0, -2, productWeight))
            it.metrics.forEach { metric -> tableHeader.addView(TextView(context).apply { text = context.tr(metric.title).uppercase(); textSize = 10f; gravity = Gravity.END; setTypeface(typeface, Typeface.BOLD); setTextColor(context.ink(R.color.workspace_muted)) }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(4) }) }
            tableTarget.addView(tableHeader)
        }
    }
    val homeListGroup = item.key in listOf("recent-sales", "stock-preview", "receivings-preview")
    val customerList = item.key == "customer-list"
    item.children.forEachIndexed { index, child ->
        if (index == 0 || (homeListGroup && index > 0)) {
            tableTarget.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)).apply {
                topMargin = if (index == 0) context.dp(6) else 0
            })
        }
        if (tableAfterControls && child.kind in tableKinds && item.children.take(index).none { it.kind in tableKinds }) {
            if (tableContent == null) addTableHeader(child) else tableTarget.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)))
        }
        tableTarget.addView(cardView(context, child, enabled, action, controls, includeTrailingDivider = !homeListGroup && !customerList))
        if (customerList && child.kind == CardKind.ROW) {
            tableTarget.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)))
        }
    }
    if (item.kind == CardKind.ROW) root.addView(View(context).apply { setBackgroundColor(context.ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, context.dp(1)).apply { topMargin = context.dp(10) })
    return root
}

private fun resourcesWide(context: Context) = context.resources.configuration.screenWidthDp >= 600

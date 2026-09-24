package com.example.superkingsale.ui

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.superkingsale.databinding.ItemCardBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.NumberFormat

fun money(value: Long) = NumberFormat.getIntegerInstance().format(value) + " MMK"
fun number(value: Long) = NumberFormat.getIntegerInstance().format(value)
fun Context.dp(value: Int) = (resources.displayMetrics.density * value).toInt()
fun LinearLayout.heading(title: String, subtitle: String = "") {
    addView(TextView(context).apply {
        text = context.tr(title); setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(com.example.superkingsale.R.dimen.foundation_text_title)); setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(context.ink(com.example.superkingsale.R.color.workspace_text))
        setPadding(0, context.dp(8), 0, context.dp(12)); contentDescription = context.tr(title)
    })
    if (subtitle.isNotBlank()) label(subtitle)
}
fun LinearLayout.label(value: String): TextView = TextView(context).also {
    it.text = context.tr(value); it.textSize = 14f; it.setTextColor(context.ink(com.example.superkingsale.R.color.workspace_muted)); it.setPadding(0, context.dp(6), 0, context.dp(10)); addView(it)
}
fun LinearLayout.button(title: String, enabled: Boolean = true, click: () -> Unit): MaterialButton =
    MaterialButton(context).also { b ->
        b.text = context.tr(title); b.isAllCaps = false; b.isEnabled = enabled
        b.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(4) }
        b.setOnClickListener { click() }; addView(b)
    }
fun LinearLayout.field(title: String, value: String = "", type: Int = InputType.TYPE_CLASS_TEXT,
    onChange: ((String) -> Unit)? = null): TextInputEditText {
    val wrapper = TextInputLayout(context).apply {
        hint = context.tr(title)
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(10); bottomMargin = context.dp(4) }
    }
    val edit = TextInputEditText(wrapper.context).apply {
        textSize = 14f
        minHeight = context.dp(56)
        filters = arrayOf(android.text.InputFilter.LengthFilter(2000))
        inputType = type; setText(value); setSingleLine(type and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0)
        isSaveEnabled = type and InputType.TYPE_TEXT_VARIATION_PASSWORD == 0
        if (android.os.Build.VERSION.SDK_INT >= 26 && type and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0) {
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
    }
    if (type and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0) wrapper.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
    wrapper.addView(edit); addView(wrapper)
    edit.doAfterTextChanged { onChange?.invoke(it.toString()) }
    return edit
}
fun LinearLayout.choice(title: String, values: List<Pair<String, String>>, selected: String,
    onSelect: (String) -> Unit): AutoCompleteTextView {
    val wrapper = TextInputLayout(context).apply {
        hint = context.tr(title); boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(10) }
    }
    val edit = com.google.android.material.textfield.MaterialAutoCompleteTextView(wrapper.context).apply {
        textSize = 14f
        inputType = InputType.TYPE_NULL
        keyListener = null
        minHeight = context.dp(56)
        setPaddingRelative(context.dp(16), context.dp(16), context.dp(48), context.dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }
    edit.setAdapter(ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, values.map { context.tr(it.second) }))
    edit.setText(context.tr(values.find { it.first == selected }?.second.orEmpty()), false)
    edit.setOnItemClickListener { _, _, index, _ -> values.getOrNull(index)?.let { onSelect(it.first) } }
    wrapper.addView(edit); addView(wrapper)
    return edit
}
fun ViewGroup.enableChildren(enabled: Boolean) {
    for (i in 0 until childCount) {
        val child = getChildAt(i); child.isEnabled = enabled
        if (child is ViewGroup) child.enableChildren(enabled)
    }
}
data class CardAction(val label: String, val key: String, val id: Long = 0)
data class Card(val key: String, val title: String, val value: String = "", val detail: String = "",
    val actions: List<CardAction> = emptyList(), val kind: CardKind = CardKind.PANEL,
    val eyebrow: String = "", val status: String = "", val metrics: List<Metric> = emptyList(),
    val children: List<Card> = emptyList(), val featured: Boolean = false)

class CardAdapter(var controls: ((LinearLayout) -> Unit)? = null, private val action: (CardAction) -> Unit) : ListAdapter<Card, CardAdapter.Holder>(
    object : DiffUtil.ItemCallback<Card>() {
        override fun areItemsTheSame(old: Card, new: Card) = old.key == new.key
        override fun areContentsTheSame(old: Card, new: Card) = old == new
    }
) {
    init { stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY }
    var busy = false
    class Holder(val container: LinearLayout) : RecyclerView.ViewHolder(container)
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
        Holder(parent.context.column().apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = context.dp(6) }
        })
    override fun onBindViewHolder(holder: Holder, position: Int) {
        (holder.container.layoutParams as RecyclerView.LayoutParams).bottomMargin = holder.container.context.dp(if (getItem(position).kind in listOf(CardKind.ROW, CardKind.TABLE_ROW)) 0 else 4)
        holder.container.removeAllViews()
        holder.container.addView(cardView(holder.container.context, getItem(position), { !busy }, action, controls))
    }
}

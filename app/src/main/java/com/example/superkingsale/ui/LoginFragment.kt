package com.example.superkingsale.ui

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.superkingsale.MainActivity
import com.example.superkingsale.R
import com.example.superkingsale.databinding.FragmentWorkspaceBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_workspace) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentWorkspaceBinding.bind(view)
        val session = (requireActivity() as MainActivity).session
        val wide = resources.getBoolean(R.bool.wide_workspace)
        b.configurePullRefresh({ !session.state.value.loading }) { session.restore() }
        b.list.isVisible = false
        b.formScroll.isVisible = true
        b.form.setPadding(0, 0, 0, 0)
        b.form.setBackgroundColor(requireContext().ink(R.color.login_page))

        val page = LinearLayout(requireContext()).apply {
            orientation = if (wide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            minimumHeight = requireContext().dp(resources.configuration.screenHeightDp)
        }
        b.form.addView(page, LinearLayout.LayoutParams(-1, -2))
        val hero = buildHero(wide)
        val formArea = FrameLayout(requireContext()).apply {
            setBackgroundColor(context.ink(R.color.login_page))
            setPadding(context.dp(if (wide) 28 else 12), context.dp(if (wide) 20 else 42),
                context.dp(if (wide) 28 else 12), context.dp(36))
        }
        if (wide) {
            page.addView(hero, LinearLayout.LayoutParams(0, -1, 1f))
            page.addView(formArea, LinearLayout.LayoutParams(0, -1, 1f))
        } else {
            page.addView(hero, LinearLayout.LayoutParams(-1, requireContext().dp(190)))
            page.addView(formArea, LinearLayout.LayoutParams(-1, -2))
        }

        val card = requireContext().column(18).apply { surface() }
        formArea.addView(card, FrameLayout.LayoutParams(if (wide) requireContext().dp(390) else -1, -2, Gravity.CENTER))
        card.eyebrow("REPRESENTATIVE WORKSPACE")
        card.copyText("Route sign in", 22f)
        card.copyText("Access your stock, customers, sales, and cash position.", 12f, color = R.color.workspace_muted)
        val username = card.field("Username or email")
        val password = card.field("Password", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val remember = CheckBox(requireContext()).apply {
            text = context.tr("Keep me signed in on this device")
            textSize = 12f
            setTextColor(context.ink(R.color.workspace_muted))
            buttonTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
            minHeight = context.dp(44)
        }
        card.addView(remember, LinearLayout.LayoutParams(-1, -2))
        val signIn = card.button("Sign in securely  ›") {
            session.login(username.text.toString(), password.text.toString(), remember.isChecked)
        }.apply {
            backgroundTintList = ColorStateList.valueOf(context.ink(R.color.workspace_accent))
            setTextColor(context.ink(R.color.workspace_surface))
        }
        card.addView(TextView(requireContext()).apply {
            text = context.tr("Sales representative access only")
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(context.ink(R.color.workspace_muted))
            setPadding(0, context.dp(10), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))

        b.retry.setOnClickListener { session.restore() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                session.state.collect { state ->
                    b.progress.isVisible = false
                    b.swipeRefresh.isRefreshing = state.loading
                    b.status.text = requireContext().tr(state.error)
                    b.status.isVisible = state.error.isNotBlank()
                    b.retry.isVisible = state.restoreFailed
                    signIn.isEnabled = !state.loading
                    signIn.text = requireContext().tr(if (state.loading) "Connecting…" else "Sign in securely  ›")
                }
            }
        }
    }

    private fun buildHero(wide: Boolean): LinearLayout = requireContext().column(if (wide) 48 else 16).apply {
        setBackgroundColor(context.ink(R.color.login_hero))
        val brand = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(context).apply {
            setImageResource(R.drawable.superking_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }, LinearLayout.LayoutParams(context.dp(38), context.dp(38)).apply { marginEnd = context.dp(10) })
        brand.addView(TextView(context).apply {
            text = "Royal Myanmar King Company\nLimited"
            textSize = if (wide) 15f else 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(android.graphics.Color.WHITE)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(brand, LinearLayout.LayoutParams(-1, -2))
        if (wide) addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
        copyText("SIMPLE MANAGEMENT. STRICT TRANSACTIONS.", 13f, true, R.color.workspace_accent)
        copyText("Your route, stock, and cash in one place.", if (wide) 36f else 22f, false, R.color.workspace_surface).apply {
            maxWidth = context.dp(if (wide) 430 else 350)
            setPadding(0, context.dp(8), 0, 0)
        }
        if (wide) {
            copyText("Secure, auditable access designed for daily inventory work.", 14f, false, R.color.workspace_surface)
            addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            copyText("INVENTORY@SALE", 11f, false, R.color.workspace_surface)
        }
    }
}

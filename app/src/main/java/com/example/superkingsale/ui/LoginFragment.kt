package com.example.superkingsale.ui

import android.os.Bundle
import android.view.View
import android.text.InputType
import androidx.fragment.app.Fragment
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.superkingsale.R
import com.example.superkingsale.MainActivity
import com.example.superkingsale.databinding.FragmentWorkspaceBinding
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_workspace) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentWorkspaceBinding.bind(view)
        val session = (requireActivity() as MainActivity).session
        b.configurePullRefresh({ !session.state.value.loading }) { session.restore() }
        b.list.isVisible = false; b.formScroll.isVisible = true
        b.form.gravity = android.view.Gravity.CENTER_HORIZONTAL
        val content = object : android.widget.LinearLayout(requireContext()) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(MeasureSpec.makeMeasureSpec(minOf(MeasureSpec.getSize(widthMeasureSpec), context.dp(480)), MeasureSpec.EXACTLY), heightMeasureSpec)
            }
        }.apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(context.dp(20), context.dp(24), context.dp(20), context.dp(24)); surface() }
        b.form.addView(content, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = requireContext().dp(24) })
        content.addView(android.widget.ImageView(requireContext()).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = android.widget.LinearLayout.LayoutParams(context.dp(96), context.dp(96)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = context.dp(12)
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        content.eyebrow("SALES WORKSPACE")
        content.heading("Super King", "Your route. Your stock. Your sales.\nSign in with your representative account.")
        val username = content.field("Username or email")
        val password = content.field("Password", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val signIn = content.button("Sign in") { session.login(username.text.toString(), password.text.toString()) }
        content.copyText("Secure connection · superkingmyanmar.com", 12f, color = R.color.workspace_muted)
        b.retry.setOnClickListener { session.restore() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                session.state.collect { state ->
                    b.progress.isVisible = false
                    b.swipeRefresh.isRefreshing = state.loading
                    b.status.text = requireContext().tr(state.error); b.status.isVisible = state.error.isNotBlank()
                    b.retry.isVisible = state.restoreFailed
                    signIn.isEnabled = !state.loading
                    signIn.text = requireContext().tr(if (state.loading) "Connecting…" else "Sign in")
                }
            }
        }
    }
}

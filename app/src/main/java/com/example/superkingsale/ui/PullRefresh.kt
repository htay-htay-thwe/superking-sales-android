package com.example.superkingsale.ui

import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.example.superkingsale.databinding.FragmentWorkspaceBinding
import com.google.android.material.color.MaterialColors

/** Shared by all main destinations; only the currently visible scroll surface owns the gesture. */
fun FragmentWorkspaceBinding.configurePullRefresh(canRefresh: () -> Boolean, refresh: () -> Unit) {
    swipeRefresh.setColorSchemeColors(MaterialColors.getColor(root, androidx.appcompat.R.attr.colorPrimary))
    swipeRefresh.setProgressBackgroundColorSchemeColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurface))
    swipeRefresh.setOnChildScrollUpCallback { _, _ ->
        !canRefresh() || (if (formScroll.isVisible) formScroll else list).canScrollVertically(-1)
    }
    swipeRefresh.setOnRefreshListener {
        if (canRefresh()) refresh() else swipeRefresh.isRefreshing = false
    }
    // TalkBack users can refresh without performing the drag gesture or finding a visible button.
    listOf(swipeRefresh, formScroll, list).forEach { target ->
        ViewCompat.addAccessibilityAction(target, root.context.tr("Refresh")) { _, _ ->
            if (canRefresh()) { refresh(); true } else false
        }
    }
}

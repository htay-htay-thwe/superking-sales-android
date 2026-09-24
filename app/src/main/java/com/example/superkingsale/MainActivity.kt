package com.example.superkingsale

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import androidx.lifecycle.*
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import com.example.superkingsale.databinding.ActivityMainBinding
import com.example.superkingsale.ui.SessionViewModel
import com.example.superkingsale.ui.tr
import com.example.superkingsale.ui.dp
import com.example.superkingsale.ui.ink
import com.example.superkingsale.ui.LocalizedDialogBuilder as MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    val repository get() = (application as SalesApplication).repository
    val session: SessionViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionViewModel(repository) as T
        })[SessionViewModel::class.java]
    }
    private lateinit var binding: ActivityMainBinding
    private val nav get() = (supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment).navController
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    var online = true
        private set
    private var hasReportedNetworkState = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateNetwork()
        override fun onLost(network: Network) = updateNetwork()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = updateNetwork()
    }
    private fun updateNetwork() = runOnUiThread {
        val wasOnline = online
        online = (application as SalesApplication).hasValidatedNetwork()
        binding.connection.isVisible = !online
        binding.onlineStatus.text = tr(if (online) "Online" else "Offline")
        if (hasReportedNetworkState && !wasOnline && online) {
            Snackbar.make(binding.root, getString(R.string.back_online), Snackbar.LENGTH_SHORT).show()
        }
        hasReportedNetworkState = true
    }
    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("appearance", MODE_PRIVATE)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        val scale = prefs.getFloat("fontScale", 1f)
        config.fontScale *= scale
        val language = prefs.getString("language", "en") ?: "en"
        config.setLocale(java.util.Locale.forLanguageTag(language))
        // PrintManager must retain the original Activity-associated context, not the
        // standalone configuration context used for localized resources/font scaling.
        super.attachBaseContext(object : android.content.ContextWrapper(newBase.createConfigurationContext(config)) {
            override fun getSystemService(name: String): Any? =
                if (name == PRINT_SERVICE) newBase.getSystemService(name) else super.getSystemService(name)
        })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction { provider.remove() }
                .start()
        }
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ink(R.color.workspace_accent)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.shell) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.appContent.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            binding.statusBarScrim.layoutParams = binding.statusBarScrim.layoutParams.apply { height = bars.top }
            val login = nav.currentDestination?.id == R.id.login
            val typing = insets.isVisible(WindowInsetsCompat.Type.ime())
            val wide = resources.getBoolean(R.bool.wide_workspace)
            binding.bottomNav.isVisible = !login && !wide && !typing
            binding.wideNav.isVisible = !login && wide && !typing
            binding.toolbar.isVisible = !login && !(typing && resources.configuration.screenHeightDp < 600)
            insets
        }
        binding.bottomNav.setOnItemSelectedListener { item -> NavigationUI.onNavDestinationSelected(item, nav) }
        binding.workspaceBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.workspaceBack.contentDescription = tr("Back")
        val accountMenu = androidx.appcompat.widget.PopupMenu(this, binding.accountMenu)
        accountMenu.menu.add(tr("My stock")).setOnMenuItemClickListener { open(R.id.stock); true }
        accountMenu.menu.add(tr("Customers")).setOnMenuItemClickListener { open(R.id.customers); true }
        accountMenu.menu.add(tr("Profile & settings")).setOnMenuItemClickListener { open(R.id.profile); true }
        accountMenu.menu.add(tr("Sign out")).setOnMenuItemClickListener {
            MaterialAlertDialogBuilder(this).setTitle("Sign out?").setMessage("Local drafts and session data will be removed.")
                .setNegativeButton("Cancel", null).setPositiveButton("Sign out") { _, _ -> session.logout() }.show(); true
        }
        binding.accountMenu.contentDescription = tr("Account menu")
        binding.accountMenu.setOnClickListener { accountMenu.show() }
        binding.workspaceLabel.text = tr("Sales workspace")
        val wide = resources.getBoolean(R.bool.wide_workspace)
        binding.workspaceLabel.isVisible = wide
        binding.onlineStatus.isVisible = wide
        binding.accountName.isVisible = wide
        if (wide) {
            binding.bottomNav.menu.clear()
            listOf(R.id.home to "Home", R.id.trip to "Trip", R.id.stock to "My stock", R.id.new_sale to "New sale", R.id.sales to "Sales", R.id.cash to "Cash").forEach { (destination, label) ->
                binding.wideNav.addView(com.google.android.material.button.MaterialButton(this).apply {
                    id = destination; text = tr(label); isAllCaps = false; textSize = 12f; minWidth = 0
                    setPadding(dp(12), 0, dp(12), 0); cornerRadius = dp(6)
                    iconSize = dp(18); iconPadding = dp(6)
                    setIconResource(when (destination) { R.id.home -> R.drawable.ic_home; R.id.trip -> R.drawable.ic_trip; R.id.stock -> R.drawable.ic_stock; R.id.new_sale -> R.drawable.ic_new_sale; R.id.sales -> R.drawable.ic_sales; else -> R.drawable.ic_cash })
                    layoutParams = android.widget.LinearLayout.LayoutParams(-2, -2)
                    setOnClickListener { NavigationUI.onNavDestinationSelected(androidx.appcompat.widget.PopupMenu(this@MainActivity, this).menu.add(0, destination, 0, label), nav) }
                })
            }
        }
        nav.addOnDestinationChangedListener { _, destination, _ ->
            val login = destination.id == R.id.login
            binding.bottomNav.isVisible = !login && !wide
            binding.wideNav.isVisible = !login && wide
            binding.toolbar.isVisible = !login
            binding.businessName.text = if (destination.id == R.id.home || destination.id == R.id.trip) {
                repository.branding.text("business_name", "Super King")
            } else {
                tr(destination.label?.toString().orEmpty())
            }
            binding.businessName.contentDescription = binding.businessName.text
            binding.accountMenu.text = repository.user.value?.name.orEmpty().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "SK" }
            binding.accountName.text = repository.user.value?.name.orEmpty()
            val top = destination.id in setOf(R.id.home, R.id.trip, R.id.new_sale, R.id.sales, R.id.cash)
            binding.workspaceBack.isVisible = !top
            if (top) binding.bottomNav.menu.findItem(destination.id)?.isChecked = true
            for (i in 0 until binding.wideNav.childCount) {
                (binding.wideNav.getChildAt(i) as com.google.android.material.button.MaterialButton).apply {
                    isSelected = id == destination.id
                    backgroundTintList = android.content.res.ColorStateList.valueOf(ink(if (isSelected) R.color.workspace_tint else R.color.workspace_background))
                    setTextColor(ink(if (isSelected) R.color.workspace_primary else R.color.workspace_muted))
                    iconTint = android.content.res.ColorStateList.valueOf(ink(if (isSelected) R.color.workspace_primary else R.color.workspace_muted))
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.user.collect { user ->
                    if (user == null && nav.currentDestination?.id != R.id.login) {
                        nav.navigate(R.id.login, null, NavOptions.Builder().setPopUpTo(R.id.main_navigation, true).build())
                    } else if (user != null && nav.currentDestination?.id == R.id.login) {
                        nav.navigate(R.id.home, null, NavOptions.Builder().setPopUpTo(R.id.login, true).build())
                    }
                }
            }
        }
        session
        connectivity.registerNetworkCallback(android.net.NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
        updateNetwork()
    }
    fun open(destination: Int, id: Long = 0) { nav.navigate(destination, Bundle().apply { putLong("recordId", id) }) }
    override fun onDestroy() {
        connectivity.unregisterNetworkCallback(callback)
        super.onDestroy()
    }
}

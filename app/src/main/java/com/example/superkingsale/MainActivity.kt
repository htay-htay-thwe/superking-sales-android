package com.example.superkingsale

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    val repository get() = (application as SalesApplication).repository
    val session: SessionViewModel by lazy {
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionViewModel(repository) as T
        })[SessionViewModel::class.java]
    }
    private lateinit var binding: ActivityMainBinding
    private val selectedDestination = mutableIntStateOf(R.id.home)
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
        // Keep the workspace visually consistent across sales devices. Users can still
        // select 1x, large, or extra-large from the in-app appearance controls.
        config.fontScale = scale
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
        binding.adaptiveNav.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.adaptiveNav.setContent {
            MaterialTheme {
                NavigationBar(
                    containerColor = Color(ink(R.color.workspace_surface)),
                    modifier = Modifier.height(64.dp),
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    listOf(
                        Triple(R.id.home, R.drawable.ic_home, "Home"),
                        Triple(R.id.trip, R.drawable.ic_trip, "Trip"),
                        Triple(R.id.new_sale, R.drawable.ic_new_sale, "New sale"),
                        Triple(R.id.sales, R.drawable.ic_sales, "Sales"),
                        Triple(R.id.cash, R.drawable.ic_cash, "Cash")
                    ).forEach { (destination, icon, label) ->
                        NavigationBarItem(
                            selected = selectedDestination.intValue == destination,
                            onClick = { open(destination) },
                            icon = { Icon(painterResource(icon), contentDescription = tr(label)) },
                            label = { Text(tr(label), fontSize = 11.sp, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(ink(R.color.workspace_primary)),
                                selectedTextColor = Color(ink(R.color.workspace_primary)),
                                indicatorColor = Color(ink(R.color.workspace_tint)),
                                unselectedIconColor = Color(ink(R.color.workspace_muted)),
                                unselectedTextColor = Color(ink(R.color.workspace_muted))
                            )
                        )
                    }
                }
            }
        }
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = ink(R.color.workspace_background)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        ViewCompat.setOnApplyWindowInsetsListener(binding.shell) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.appContent.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            binding.statusBarScrim.layoutParams = binding.statusBarScrim.layoutParams.apply { height = bars.top }
            val login = nav.currentDestination?.id == R.id.login
            val typing = insets.isVisible(WindowInsetsCompat.Type.ime())
            val wide = resources.getBoolean(R.bool.wide_workspace)
            binding.bottomNav.isVisible = false
            binding.adaptiveNav.isVisible = !login && !wide && !typing
            binding.wideNav.isVisible = !login && wide && !typing
            binding.toolbar.isVisible = !login && !(typing && resources.configuration.screenHeightDp < 600)
            insets
        }
        binding.workspaceBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.workspaceBack.contentDescription = tr("Back")
        val appearance = getSharedPreferences("appearance", MODE_PRIVATE)
        binding.accountMenu.contentDescription = tr("Account menu")
        binding.accountMenu.setOnClickListener { showAccountMenu(appearance) }
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
            binding.bottomNav.isVisible = false
            binding.adaptiveNav.isVisible = !login && !wide
            binding.wideNav.isVisible = !login && wide
            binding.toolbar.isVisible = !login
            // The web reference keeps one persistent branded app bar on every route.
            // Screen identity belongs in the page header, not in a second toolbar style.
            binding.businessName.text = repository.branding.text("business_name", "Super King")
            binding.businessName.contentDescription = binding.businessName.text
            val initials = repository.user.value?.name.orEmpty().split(" ")
                .mapNotNull { word -> word.firstOrNull { it.isLetter() } }
                .take(2).joinToString("") { it.uppercase() }
            binding.accountMenu.text = when (initials.length) { 0 -> "SK"; 1 -> initials + "C"; else -> initials }
            binding.accountName.text = repository.user.value?.name.orEmpty()
            val top = destination.id in setOf(R.id.home, R.id.trip, R.id.new_sale, R.id.sales, R.id.cash, R.id.customers)
            binding.workspaceBack.isVisible = !top
            if (top) selectedDestination.intValue = destination.id
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

    private fun showAccountMenu(appearance: android.content.SharedPreferences) {
        val width = dp(if (resources.getBoolean(R.bool.wide_workspace)) 320 else 280)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(ink(R.color.workspace_surface))
                setStroke(dp(1), ink(R.color.workspace_line))
            }
        }
        val popup = PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = dp(10).toFloat()
            isOutsideTouchable = true
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        val user = repository.user.value
        val userName = user?.name.orEmpty().ifBlank { tr("Sales representative") }
        val initials = userName.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("").ifBlank { "SK" }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(10))
        }
        header.addView(TextView(this).apply {
            text = initials; gravity = Gravity.CENTER; textSize = 12f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(ink(R.color.workspace_surface))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ink(R.color.workspace_accent)) }
        }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply { text = userName; textSize = 13f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink(R.color.workspace_text)); maxLines = 1 })
            addView(TextView(this@MainActivity).apply { text = "@${user?.text("username").orEmpty()} · ${tr("Sales representative")}"; textSize = 11f; setTextColor(ink(R.color.workspace_muted)); maxLines = 1 })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(header)
        content.addView(View(this).apply { setBackgroundColor(ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, dp(1)))
        fun row(icon: Int, label: String, danger: Boolean = false, action: () -> Unit) {
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(42); isClickable = true; isFocusable = true
                setPadding(dp(14), 0, dp(14), 0)
                background = android.util.TypedValue().let { value ->
                    theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
                    androidx.appcompat.content.res.AppCompatResources.getDrawable(this@MainActivity, value.resourceId)
                }
                setOnClickListener { popup.dismiss(); action() }
            }
            item.addView(ImageView(this).apply {
                setImageResource(icon); imageTintList = android.content.res.ColorStateList.valueOf(ink(if (danger) R.color.workspace_error else R.color.workspace_text))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { marginEnd = dp(12) })
            item.addView(TextView(this).apply {
                text = tr(label); textSize = 12f; setTypeface(typeface, Typeface.BOLD)
                setTextColor(ink(if (danger) R.color.workspace_error else R.color.workspace_text))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            content.addView(item, LinearLayout.LayoutParams(-1, dp(42)))
        }
        row(R.drawable.ic_customer, "Customers") { open(R.id.customers) }
        row(R.drawable.ic_account, "Profile & security") { open(R.id.profile) }
        row(R.drawable.ic_theme, if (appearance.getInt("theme", 1) == AppCompatDelegate.MODE_NIGHT_YES) "Use light theme" else "Use dark theme") {
            val next = if (appearance.getInt("theme", 1) == AppCompatDelegate.MODE_NIGHT_YES) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
            appearance.edit().putInt("theme", next).apply(); AppCompatDelegate.setDefaultNightMode(next)
        }
        row(R.drawable.ic_density, if (appearance.getString("density", "compact") == "compact") "Use comfortable density" else "Use compact density") {
            appearance.edit().putString("density", if (appearance.getString("density", "compact") == "compact") "comfortable" else "compact").apply(); recreate()
        }
        row(R.drawable.ic_language, if (appearance.getString("language", "en") == "my") "English" else "မြန်မာ") {
            appearance.edit().putString("language", if (appearance.getString("language", "en") == "my") "en" else "my").apply(); recreate()
        }
        row(R.drawable.ic_print, "Print settings") { open(R.id.profile) }
        content.addView(View(this).apply { setBackgroundColor(ink(R.color.workspace_line)) }, LinearLayout.LayoutParams(-1, dp(1)))
        row(R.drawable.ic_sign_out, "Sign out", true) {
            MaterialAlertDialogBuilder(this).setTitle("Sign out?").setMessage("Local drafts and session data will be removed.")
                .setNegativeButton("Cancel", null).setPositiveButton("Sign out") { _, _ -> session.logout() }.show()
        }
        popup.showAsDropDown(binding.accountMenu, binding.accountMenu.width - width, dp(4))
    }
    override fun onDestroy() {
        connectivity.unregisterNetworkCallback(callback)
        super.onDestroy()
    }
}

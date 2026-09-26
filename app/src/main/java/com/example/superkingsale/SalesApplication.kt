package com.example.superkingsale

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.superkingsale.data.SalesRepository
import com.example.superkingsale.data.SecureStore

open class SalesApplication : Application() {
    open val repository: SalesRepository by lazy { SalesRepository(SecureStore(this)) }
    open fun hasValidatedNetwork(): Boolean {
        val manager = getSystemService(android.net.ConnectivityManager::class.java)
        return manager.getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }
    override fun onCreate() {
        super.onCreate()
        val theme = getSharedPreferences("appearance", MODE_PRIVATE).getInt("theme", AppCompatDelegate.MODE_NIGHT_NO)
        AppCompatDelegate.setDefaultNightMode(theme)
    }
}

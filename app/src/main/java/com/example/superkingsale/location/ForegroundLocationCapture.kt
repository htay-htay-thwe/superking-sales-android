package com.example.superkingsale.location

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.superkingsale.ui.LocalizedDialogBuilder
import com.example.superkingsale.ui.tr
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Construct during Fragment initialization so permission registration survives recreation. No background tracking. */
class ForegroundLocationCapture(private val fragment: Fragment) : DefaultLifecycleObserver {
    private val context get() = fragment.requireContext()
    private var manager: LocationManager? = null
    private var listener: LocationListener? = null
    private var timeout: Job? = null
    private var dialog: androidx.appcompat.app.AlertDialog? = null
    private var callback: ((Location) -> Unit)? = null
    private var best: Location? = null
    private var awaitingSettings = false
    private val permissions = fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (granted(Manifest.permission.ACCESS_COARSE_LOCATION) || granted(Manifest.permission.ACCESS_FINE_LOCATION)) acquire()
        else {
            settings("Location permission is required to create a sale. Allow location in Android app settings, then retry.", true)
        }
    }
    init { fragment.lifecycle.addObserver(this) }
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun start(result: (Location) -> Unit) {
        cancel(); callback = result
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION) && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            dialog = LocalizedDialogBuilder(context).setTitle("Location access")
                .setMessage("Your location is requested only while this screen is open. It is attached to a sale only when you save that sale. Choose precise location for better accuracy.")
                .setNegativeButton("Cancel") { _, _ -> callback = null }
                .setPositiveButton("Continue") { _, _ -> permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                .setOnCancelListener { callback = null }.show()
        } else acquire()
    }
    private fun settings(message: String, app: Boolean) {
        dialog?.dismiss()
        dialog = LocalizedDialogBuilder(context).setTitle("Location settings").setMessage(message)
            .setNegativeButton("Cancel") { _, _ -> cancel() }.setPositiveButton("Open settings") { _, _ ->
                val intent = if (app) Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    else Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                awaitingSettings = true
                runCatching { fragment.startActivity(intent) }
            }.show()
    }
    private fun usable(location: Location): Boolean {
        if (!location.hasAccuracy()) return false
        return if (location.elapsedRealtimeNanos > 0L) FixQuality.usable(location.latitude, location.longitude,
            location.accuracy, location.elapsedRealtimeNanos, SystemClock.elapsedRealtimeNanos())
        else FixQuality.usableWallTime(location.latitude, location.longitude, location.accuracy,
            location.time, System.currentTimeMillis())
    }
    private fun acquire() {
        if (callback == null || !fragment.isAdded) return
        dialog?.dismiss()
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) { settings("Location permission is required.", true); return }
        val service = context.getSystemService(LocationManager::class.java)
        manager = service
        val priority = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        val enabled = runCatching { service.getProviders(true) }.getOrDefault(emptyList())
        val providers = (priority + enabled).distinct().filter { provider ->
            provider in enabled && (fine || provider != LocationManager.GPS_PROVIDER)
        }
        if (providers.isEmpty()) { settings("Turn on device location, then return to this app. Location capture will retry automatically.", false); return }
        best = null
        providers.mapNotNull { provider -> runCatching { service.getLastKnownLocation(provider) }.getOrNull() }
            .filter(::usable).minByOrNull { it.accuracy }?.let { deliver(it); return }
        dialog = LocalizedDialogBuilder(context).setTitle("Getting current location…")
            .setMessage("Getting a location from this device. Move outdoors or near a window if needed.")
            .setNegativeButton("Cancel") { _, _ -> cancel() }.setOnCancelListener { cancel() }.show()
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!usable(location) || callback == null) return
                if (best == null || !usable(best!!) || location.accuracy <= best!!.accuracy) best = Location(location)
                // Accuracy is recorded with the sale; do not make the user wait for an arbitrary GPS threshold.
                deliver(best!!)
            }
            @Deprecated("Platform compatibility") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            val requested = providers.count { provider ->
                runCatching { service.requestLocationUpdates(provider, 1000L, 0f, listener!!, Looper.getMainLooper()) }.isSuccess
            }
            if (requested == 0) { stopUpdates(); settings("This device did not allow any location provider. Check app permission and device location, then return to retry.", true); return }
            timeout = fragment.lifecycleScope.launch {
                for (remaining in 30 downTo 1) {
                    dialog?.setMessage(context.tr("Getting a location from this device. Move outdoors or near a window if needed.") +
                        "\n" + context.tr("Seconds remaining: $remaining") + (best?.let { "\n" + context.tr("Accuracy: ${it.accuracy.toInt()} m") } ?: ""))
                    delay(1000)
                }
                val fix = best?.takeIf(::usable)
                if (fix != null) confirmApproximate(fix) else {
                    stopUpdates(); best = null; dialog?.dismiss()
                    dialog = LocalizedDialogBuilder(context).setTitle("Location unavailable")
                        .setMessage("No fresh location was received. Move outdoors, check device location settings and retry. Your sale draft is unchanged.")
                        .setNegativeButton("Cancel") { _, _ -> cancel() }
                        .setPositiveButton("Retry") { _, _ -> callback?.let { acquire() } }
                        .setOnCancelListener { cancel() }.show()
                }
            }
        } catch (_: SecurityException) { stopUpdates(); settings("Location permission is required.", true) }
        catch (_: IllegalArgumentException) { stopUpdates(); settings("Turn on device location, then return to retry automatically.", false) }
    }
    private fun confirmApproximate(fix: Location) {
        stopUpdates(); dialog?.dismiss()
        dialog = LocalizedDialogBuilder(context).setTitle("Check location accuracy")
            .setMessage(context.tr("This location is approximate. You may retry outdoors or allow precise location in app settings.") + "\n\n" + describe(fix))
            .setNegativeButton("Cancel") { _, _ -> callback = null }
            .setNeutralButton("App settings") { _, _ -> settings("Allow precise location in Android app settings, then return to retry automatically.", true) }
            .setPositiveButton("Use this location") { _, _ ->
                if (usable(fix)) deliver(fix) else { val next = callback; cancel(); next?.let(::start) }
            }.setOnCancelListener { callback = null }.show()
    }
    private fun deliver(fix: Location) {
        val result = callback
        cancel()
        result?.invoke(fix)
    }
    fun describe(fix: Location): String = listOf(
        String.format(Locale.US, "%.6f, %.6f", fix.latitude, fix.longitude),
        context.tr("Accuracy: ${fix.accuracy.toInt()} m"),
        context.tr("Provider: ${fix.provider.orEmpty()}"),
        context.tr(if (LocationCompat.isMock(fix)) "Simulated location — not a physical GPS acceptance result." else "Device location — check against your actual position.")
    ).joinToString("\n")
    private fun stopUpdates() {
        timeout?.cancel(); timeout = null
        listener?.let { runCatching { manager?.removeUpdates(it) } }; listener = null
    }
    fun cancel() { stopUpdates(); callback = null; best = null; awaitingSettings = false; dialog?.dismiss(); dialog = null }
    override fun onResume(owner: LifecycleOwner) {
        if (awaitingSettings) { awaitingSettings = false; acquire() }
    }
    override fun onStop(owner: LifecycleOwner) { if (!awaitingSettings) cancel() else stopUpdates() }
    override fun onDestroy(owner: LifecycleOwner) { cancel() }
}

package com.example.superkingsale

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.superkingsale.location.FixQuality
import org.junit.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit opt-in, no mock providers and no API writes. Do not run on the user's device without approval. */
@RunWith(AndroidJUnit4::class)
class PhysicalGpsTest {
    @Test fun approvedPhysicalForegroundFix() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("allowPhysicalGps") == "YES")
        Assume.assumeFalse(android.os.Build.FINGERPRINT.contains("generic"))
        val app = ApplicationProvider.getApplicationContext<TestSalesApplication>()
        app.seed()
        Assert.assertEquals(PackageManager.PERMISSION_GRANTED, ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION))
        ActivityScenario.launch(MainActivity::class.java).use {
            val manager = app.getSystemService(LocationManager::class.java)
            Assert.assertTrue("Device GPS is disabled", manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
            val latch = CountDownLatch(1)
            var fix: Location? = null
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!LocationCompat.isMock(location) && location.hasAccuracy() && FixQuality.usable(location.latitude, location.longitude,
                            location.accuracy, location.elapsedRealtimeNanos, android.os.SystemClock.elapsedRealtimeNanos())) {
                        fix = Location(location); latch.countDown()
                    }
                }
                @Deprecated("Platform compatibility") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            try {
                manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, listener, Looper.getMainLooper())
                Assert.assertTrue("No fresh physical GPS fix within 45 seconds; move outdoors and retry", latch.await(45, TimeUnit.SECONDS))
                val measured = checkNotNull(fix)
                // Intentionally report only provider/accuracy, not the user's precise coordinates.
                InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                    putString("stream", "PHYSICAL GPS: provider=${measured.provider}; reported_accuracy_m=${measured.accuracy}; mock=false\n")
                })
                Assert.assertTrue(app.requests.none { it.method != "GET" })
            } finally { manager.removeUpdates(listener) }
        }
    }
}

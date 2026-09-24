package com.example.superkingsale.location

/** Monotonic age checks are independent of clock/time-zone changes. Accuracy is not a business limit. */
object FixQuality {
    const val MAX_AGE_NANOS = 30_000_000_000L
    const val PREFERRED_ACCURACY_METERS = 50f
    fun usable(latitude: Double, longitude: Double, accuracy: Float, fixNanos: Long, nowNanos: Long): Boolean =
        latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0 &&
            accuracy.isFinite() && accuracy > 0 && fixNanos > 0 && nowNanos >= fixNanos && nowNanos - fixNanos <= MAX_AGE_NANOS
}

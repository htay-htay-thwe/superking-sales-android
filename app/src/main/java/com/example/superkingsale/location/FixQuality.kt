package com.example.superkingsale.location

/** Monotonic age checks are independent of clock/time-zone changes. Accuracy is not a business limit. */
object FixQuality {
    // Matches the sale freshness rule and avoids needless waits on slow OEM providers.
    const val MAX_AGE_NANOS = 120_000_000_000L
    const val MAX_AGE_MILLIS = 120_000L
    const val PREFERRED_ACCURACY_METERS = 50f
    fun usable(latitude: Double, longitude: Double, accuracy: Float, fixNanos: Long, nowNanos: Long): Boolean =
        latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0 &&
            accuracy.isFinite() && accuracy > 0 && fixNanos > 0 && nowNanos >= fixNanos && nowNanos - fixNanos <= MAX_AGE_NANOS

    /** Older/OEM providers can omit the monotonic timestamp. */
    fun usableWallTime(latitude: Double, longitude: Double, accuracy: Float, fixMillis: Long, nowMillis: Long): Boolean =
        latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0 &&
            accuracy.isFinite() && accuracy > 0 && fixMillis > 0 && nowMillis >= fixMillis && nowMillis - fixMillis <= MAX_AGE_MILLIS
}

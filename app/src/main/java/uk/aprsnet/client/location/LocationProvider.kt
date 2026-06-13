package uk.aprsnet.client.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A position fix from the device GPS / fused location provider.
 */
data class Fix(
    val lat: Double,
    val lon: Double,
    val speedKmh: Double,
    val course: Int,          // heading 0..359, -1 if unknown
    val accuracyM: Float,
    val time: Long
)

/**
 * Wraps Google Play Services FusedLocationProviderClient and exposes the
 * latest device position as a Flow. Used both for drawing the user's own
 * marker on the map and for feeding the smart-beacon algorithm.
 */
class LocationProvider(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    private val _lastFix = MutableStateFlow<Fix?>(null)
    val lastFix: StateFlow<Fix?> = _lastFix

    private var running = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            _lastFix.value = Fix(
                lat = loc.latitude,
                lon = loc.longitude,
                speedKmh = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0,
                course = if (loc.hasBearing()) loc.bearing.toInt() else -1,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else 0f,
                time = loc.time
            )
        }
    }

    /**
     * Begin location updates. Caller must hold ACCESS_FINE_LOCATION.
     * The suppress is safe: callers check the permission before invoking.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 10_000L
        ).setMinUpdateIntervalMillis(5_000L).build()
        runCatching {
            client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
        }
    }

    fun stop() {
        running = false
        runCatching { client.removeLocationUpdates(callback) }
    }
}
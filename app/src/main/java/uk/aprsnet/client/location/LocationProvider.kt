package uk.aprsnet.client.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
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
 * Motion state — used to scale GPS polling interval and accuracy tier.
 * Transitions are driven by BeaconManager after each incoming fix.
 */
enum class MotionState {
    /** Speed >= 5 km/h — HIGH_ACCURACY, 8 s interval */
    MOVING,
    /** Speed 1–5 km/h or shortly after stopping — HIGH_ACCURACY, 20 s interval */
    SLOW,
    /** Speed < 1 km/h for > 60 s — BALANCED_POWER, 150 s interval.
     *  SmartBeacon slow rate is 20 min: 150 s gives a margin of ~8 fixes per beacon cycle
     *  while reducing GPS wakeups by ~95 % compared to the stationary 5 s baseline. */
    STATIONARY
}

/**
 * Wraps Google Play Services FusedLocationProviderClient and exposes the
 * latest device position as a Flow.
 *
 * Uses a dedicated HandlerThread for GPS callbacks so Android's job-scheduler
 * can batch wakeups correctly without touching the main thread.
 *
 * GPS polling rate is adaptive via [setMotionState]:
 *   MOVING      → HIGH_ACCURACY  8 s / 5 s min
 *   SLOW        → HIGH_ACCURACY  20 s / 15 s min
 *   STATIONARY  → BALANCED       150 s / 120 s min  (+ 180 s max batch delay)
 */
class LocationProvider(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    private val _lastFix = MutableStateFlow<Fix?>(null)
    val lastFix: StateFlow<Fix?> = _lastFix

    private var running = false
    private var currentState = MotionState.MOVING

    // Dedicated thread keeps GPS callbacks off the main thread and allows
    // the scheduler to batch wakeups when the phone is in doze.
    private val gpsThread = HandlerThread("gps-loc-cb").also { it.start() }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            _lastFix.value = Fix(
                lat       = loc.latitude,
                lon       = loc.longitude,
                speedKmh  = if (loc.hasSpeed()) loc.speed * 3.6 else 0.0,
                course    = if (loc.hasBearing()) loc.bearing.toInt() else -1,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else 0f,
                time      = loc.time
            )
        }
    }

    /**
     * Begin location updates. Caller must hold ACCESS_FINE_LOCATION.
     */
    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        issueRequest()
    }

    fun stop() {
        running = false
        runCatching { client.removeLocationUpdates(callback) }
    }

    /**
     * Called by BeaconManager after each fix to adapt the polling rate.
     * No-op if the state hasn't changed, so it's safe to call every fix.
     */
    @SuppressLint("MissingPermission")
    fun setMotionState(state: MotionState) {
        if (state == currentState || !running) return
        currentState = state
        issueRequest()
    }

    @SuppressLint("MissingPermission")
    private fun issueRequest() {
        runCatching { client.removeLocationUpdates(callback) }
        val request = when (currentState) {
            MotionState.MOVING ->
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 8_000L)
                    .setMinUpdateIntervalMillis(5_000L)
                    .build()

            MotionState.SLOW ->
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 20_000L)
                    .setMinUpdateIntervalMillis(15_000L)
                    .setMaxUpdateDelayMillis(25_000L)
                    .build()

            MotionState.STATIONARY ->
                LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 150_000L)
                    .setMinUpdateIntervalMillis(120_000L)
                    .setMaxUpdateDelayMillis(180_000L)
                    .build()
        }
        runCatching {
            client.requestLocationUpdates(request, callback, gpsThread.looper)
        }
    }
}

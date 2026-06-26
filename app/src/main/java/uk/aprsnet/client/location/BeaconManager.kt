package uk.aprsnet.client.location

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.aprsnet.client.aprs.PacketBuilder
import uk.aprsnet.client.data.SettingsStore
import uk.aprsnet.client.net.AprsWebSocket

/**
 * Drives position beaconing: watches the LocationProvider fix stream, runs
 * each fix through the SmartBeacon algorithm, and transmits a position
 * packet over the WebSocket when a beacon is due.
 *
 * Also exposes the user's current position so the map can draw their marker.
 */
class BeaconManager(
    private val location: LocationProvider,
    private val ws: AprsWebSocket,
    private val settings: SettingsStore
) {
    // SmartBeacon is re-created whenever beaconing starts so that settings
    // changes (min interval, slow/fast rate) take effect on next session.
    private fun buildSmartBeacon() = SmartBeacon(
        minBeaconSec  = settings.smartMinSec,
        slowRateSec   = settings.smartSlowRateSec,
        fastRateSec   = settings.smartFastRateSec
    )
    private var smartBeacon = buildSmartBeacon()

    private val _myPosition = MutableStateFlow<Fix?>(null)
    /** The user's latest position, for the "my location" map marker. */
    val myPosition: StateFlow<Fix?> = _myPosition

    private val _lastBeaconAt = MutableStateFlow(0L)
    val lastBeaconAt: StateFlow<Long> = _lastBeaconAt

    private var started = false

    // ── Motion state tracking for adaptive GPS ────────────────────────────────
    private val STATIONARY_SPEED_KMH = 1.0
    private val MOVING_SPEED_KMH     = 5.0
    /** Millis at which speed first dropped below STATIONARY_SPEED_KMH. */
    private var stationarySince = 0L
    /** Delay before committing to STATIONARY to avoid false positives at traffic lights. */
    private val STATIONARY_CONFIRM_MS = 60_000L

    private fun motionStateFor(fix: Fix): MotionState = when {
        fix.speedKmh >= MOVING_SPEED_KMH -> {
            stationarySince = 0L
            MotionState.MOVING
        }
        fix.speedKmh >= STATIONARY_SPEED_KMH -> {
            stationarySince = 0L
            MotionState.SLOW
        }
        else -> {
            if (stationarySince == 0L) stationarySince = System.currentTimeMillis()
            if (System.currentTimeMillis() - stationarySince >= STATIONARY_CONFIRM_MS)
                MotionState.STATIONARY
            else
                MotionState.SLOW
        }
    }

    /** Begin observing fixes. Caller ensures location permission is held. */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        smartBeacon = buildSmartBeacon()
        stationarySince = 0L
        location.start()
        scope.launch {
            location.lastFix.collect { fix ->
                if (fix == null) return@collect
                _myPosition.value = fix
                location.setMotionState(motionStateFor(fix))
                maybeBeacon(fix)
            }
        }
    }

    fun stop() {
        started = false
        location.stop()
    }

    /** Transmit a position beacon now, regardless of the smart-beacon timer. */
    fun beaconNow() {
        _myPosition.value?.let { transmit(it) }
    }

    private fun maybeBeacon(fix: Fix) {
        if (settings.positionMode != "smart") return
        if (!settings.hasCredentials) return
        if (smartBeacon.shouldBeacon(fix, System.currentTimeMillis())) {
            transmit(fix)
        }
    }

    private fun transmit(fix: Fix) {
        if (!settings.hasCredentials) return
        val table = settings.symbolTable.firstOrNull() ?: '/'
        val code = settings.symbolCode.firstOrNull() ?: '>'
        val comment = buildString {
            if (fix.course >= 0 && fix.speedKmh > 1) {
                // APRS course/speed extension: CSE/SPD before the comment
                val cse = fix.course.toString().padStart(3, '0')
                val spdKnots = (fix.speedKmh / 1.852).toInt().toString().padStart(3, '0')
                append(cse).append('/').append(spdKnots)
            }
            append(settings.beaconComment)
        }
        val packet = PacketBuilder.position(
            from = settings.fullCallsign,    // base or base-N depending on SSID setting
            lat = fix.lat,
            lon = fix.lon,
            symbolTable = table,
            symbolCode = code,
            comment = comment
        )
        if (ws.transmit(packet)) {
            _lastBeaconAt.value = System.currentTimeMillis()
            // Send status packet if the user has set one
            val statusText = settings.statusText
            if (statusText.isNotEmpty()) {
                ws.transmit(PacketBuilder.status(settings.fullCallsign, statusText))
            }
        }
    }
}
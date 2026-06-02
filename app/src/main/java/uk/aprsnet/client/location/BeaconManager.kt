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
    private val smartBeacon = SmartBeacon()

    private val _myPosition = MutableStateFlow<Fix?>(null)
    /** The user's latest position, for the "my location" map marker. */
    val myPosition: StateFlow<Fix?> = _myPosition

    private val _lastBeaconAt = MutableStateFlow(0L)
    val lastBeaconAt: StateFlow<Long> = _lastBeaconAt

    private var started = false

    /** Begin observing fixes. Caller ensures location permission is held. */
    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        smartBeacon.reset()
        location.start()
        scope.launch {
            location.lastFix.collect { fix ->
                if (fix == null) return@collect
                _myPosition.value = fix
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
        }
    }
}
package uk.aprsnet.client

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.aprsnet.client.aprs.PacketParser
import uk.aprsnet.client.data.AppDatabase
import uk.aprsnet.client.data.MessageEntity
import uk.aprsnet.client.data.MessageRepository
import uk.aprsnet.client.data.SettingsStore
import uk.aprsnet.client.location.BeaconManager
import uk.aprsnet.client.location.Fix
import uk.aprsnet.client.location.LocationProvider
import uk.aprsnet.client.model.Station
import uk.aprsnet.client.model.StationType
import uk.aprsnet.client.net.AprsWebSocket

/**
 * Holds the live application state.
 *  Stage 1: WebSocket + station map.
 *  Stage 2: messaging (repository, ACK/green bubble, notifications).
 *  Stage 3: smart beaconing + the user's own GPS position on the map.
 */
class AprsViewModel(app: Application) : AndroidViewModel(app) {

    val ws = AprsWebSocket()

    private val db = AppDatabase.get(app)
    val messages = MessageRepository(db.messageDao(), ws)

    val settings = SettingsStore(app)
    private val location = LocationProvider(app)
    val beacon = BeaconManager(location, ws, settings)

    private val _stations = MutableStateFlow<Map<String, Station>>(emptyMap())
    val stations: StateFlow<Map<String, Station>> = _stations

    val connState: StateFlow<AprsWebSocket.ConnState> = ws.state

    /** The user's own position for the map marker. */
    val myPosition: StateFlow<Fix?> = beacon.myPosition

    /** Emitted when a message addressed to us arrives - drives notifications. */
    val incomingMessages = MutableSharedFlow<MessageEntity>(extraBufferCapacity = 32)

    val conversations = messages.conversations()
    val totalUnread = messages.totalUnread()

    init {
        viewModelScope.launch {
            ws.positionData.collect { json ->
                val call = json.optString("call")
                if (call.isNotEmpty() && json.has("lat") && json.has("lon")) {
                    _stations.value = _stations.value + (call to Station(
                        callsign = call,
                        lat = json.optDouble("lat"),
                        lon = json.optDouble("lon"),
                        comment = json.optString("raw", ""),
                        path = json.optString("path", ""),
                        raw = json.optString("raw", ""),
                        lastHeard = System.currentTimeMillis(),
                        type = StationType.HAM
                    ))
                }
            }
        }
        viewModelScope.launch {
            ws.rawPackets.collect { raw ->
                when (val p = PacketParser.parse(raw)) {
                    is PacketParser.Parsed.Pos ->
                        _stations.value =
                            _stations.value + (p.station.callsign to p.station)
                    is PacketParser.Parsed.Msg -> {
                        val incoming = messages.handleIncoming(raw)
                        if (incoming != null) incomingMessages.tryEmit(incoming)
                    }
                    else -> {}
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                runCatching { messages.retrySweep() }
            }
        }
    }

    fun start(callsign: String, passcode: String) {
        val call = callsign.ifEmpty { settings.callsign }
        val pass = passcode.ifEmpty { settings.passcode }
        messages.myCallsign = call.trim().uppercase()
        if (call.isNotEmpty()) ws.setCredentials(call, pass)
        ws.connect()
        startBeaconingIfPermitted()
    }

    /** Begin smart beaconing if location permission is held and mode is on. */
    fun startBeaconingIfPermitted() {
        val ctx = getApplication<Application>()
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted && settings.positionMode != "off") {
            beacon.start(viewModelScope)
        }
    }

    /** Transmit a position beacon immediately (manual "beacon now"). */
    fun beaconNow() = beacon.beaconNow()

    fun thread(call: String) = messages.thread(call)

    fun send(to: String, text: String) {
        viewModelScope.launch { runCatching { messages.sendMessage(to, text) } }
    }

    fun retry(rowId: Long) {
        viewModelScope.launch { runCatching { messages.retry(rowId) } }
    }

    fun markRead(call: String) {
        viewModelScope.launch { runCatching { messages.markRead(call) } }
    }

    override fun onCleared() {
        beacon.stop()
        ws.disconnect()
        super.onCleared()
    }
}
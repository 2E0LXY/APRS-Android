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
import uk.aprsnet.client.data.ContactEntity
import uk.aprsnet.client.data.MessageEntity
import uk.aprsnet.client.data.MessageRepository
import uk.aprsnet.client.data.SettingsStore
import uk.aprsnet.client.location.BeaconManager
import uk.aprsnet.client.location.Fix
import uk.aprsnet.client.location.LocationProvider
import uk.aprsnet.client.model.Station
import uk.aprsnet.client.model.StationType
import uk.aprsnet.client.net.AprsApi
import uk.aprsnet.client.net.AprsWebSocket

/**
 * Holds the live application state.
 *  Stage 1: WebSocket + station map.
 *  Stage 2: messaging (repository, ACK/green bubble, notifications).
 *  Stage 3: smart beaconing + the user's own GPS position on the map.
 *  Stage 4: contacts, stations list, status/analytics.
 */
class AprsViewModel(app: Application) : AndroidViewModel(app) {

    val ws = AprsWebSocket()

    private val db = AppDatabase.get(app)
    val messages = MessageRepository(db.messageDao(), ws)
    private val contactDao = db.contactDao()

    val settings = SettingsStore(app)
    private val location = LocationProvider(app)
    val beacon = BeaconManager(location, ws, settings)

    private val _stations = MutableStateFlow<Map<String, Station>>(emptyMap())
    val stations: StateFlow<Map<String, Station>> = _stations

    val connState: StateFlow<AprsWebSocket.ConnState> = ws.state
    val myPosition: StateFlow<Fix?> = beacon.myPosition

    val incomingMessages = MutableSharedFlow<MessageEntity>(extraBufferCapacity = 32)

    val conversations = messages.conversations()
    val totalUnread = messages.totalUnread()

    /** Saved contacts. */
    val contacts = contactDao.all()

    /** Latest server status (refreshed periodically). */
    private val _status = MutableStateFlow<AprsApi.Status?>(null)
    val status: StateFlow<AprsApi.Status?> = _status

    /** Bumped whenever a map filter is toggled so MapScreen re-applies filters immediately. */
    private val _filterTick = MutableStateFlow(0)
    val filterTick: StateFlow<Int> = _filterTick
    fun tickFilters() { _filterTick.value++ }

    init {
        viewModelScope.launch {
            ws.positionData.collect { json ->
                val call = json.optString("call")
                if (call.isNotEmpty() && json.has("lat") && json.has("lon")) {
                    val sym = json.optString("sym", "")
                    val symCode = if (sym.length >= 2) sym[1] else ' '
                    val type = when {
                        symCode == '_' -> StationType.WEATHER
                        symCode == '\'' || symCode == 'g' -> StationType.GLIDER
                        symCode == 's' || symCode == 'Y' -> StationType.SHIP
                        else -> StationType.HAM
                    }
                    _stations.value = _stations.value + (call to Station(
                        callsign = call,
                        lat = json.optDouble("lat"),
                        lon = json.optDouble("lon"),
                        symbolTable = if (sym.isNotEmpty()) sym[0] else '/',
                        symbolCode = symCode,
                        comment = json.optString("raw", ""),
                        path = json.optString("path", ""),
                        raw = json.optString("raw", ""),
                        lastHeard = System.currentTimeMillis(),
                        type = type
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
        // poll server status every 30s
        viewModelScope.launch {
            while (true) {
                runCatching { _status.value = AprsApi.status() }
                delay(30_000)
            }
        }
    }

    fun start(callsign: String, passcode: String) {
        val call = callsign.ifEmpty { settings.callsign }
        val pass = passcode.ifEmpty { settings.passcode }
        messages.myCallsign = settings.fullCallsign     // includes SSID suffix if set
        if (call.isNotEmpty()) ws.setCredentials(call, pass)
        ws.connect()
        startBeaconingIfPermitted()
    }

    fun startBeaconingIfPermitted() {
        val ctx = getApplication<Application>()
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted && settings.positionMode != "off") {
            beacon.start(viewModelScope)
        }
    }

    fun beaconNow() = beacon.beaconNow()

    /**
     * Sign in to the user's website account.
     *  - If success: stores the token + name in settings and auto-fills the
     *    callsign + passcode from the server's response, then re-authenticates
     *    the WebSocket so messaging works immediately.
     *  - If failure: returns the error string so the UI can show it.
     */
    suspend fun loginMember(callsign: String, password: String): String? {
        val r = AprsApi.memberLogin(callsign, password)
        if (!r.ok) return r.error ?: "Login failed"
        settings.callsign = r.callsign.ifEmpty { callsign }
        if (r.passcode.isNotEmpty()) settings.passcode = r.passcode
        settings.memberToken = r.token
        settings.memberName = r.name
        applySettings()
        return null
    }

    fun signOutMember() {
        settings.clearMember()
    }

    /**
     * Re-apply settings after the user saves them. Reconnects the WS with
     * the current callsign/passcode and re-starts beaconing if permitted.
     *
     * When the user has saved credentials for the FIRST time we flip the
     * default position mode from "off" to "smart" so they don't have to
     * dig into a second card to start showing on the map.
     *
     * After (re)starting beaconing, if smart mode is on AND a GPS fix is
     * already known, force an immediate beacon - otherwise a stationary user
     * could wait up to 20 minutes for the smart-beacon timer to fire the
     * first packet (slowRateSec default).
     */
    fun applySettings() {
        val call = settings.callsign
        val pass = settings.passcode
        messages.myCallsign = settings.fullCallsign
        // First-save default: turn beaconing on so the user appears on aprsnet.uk
        if (call.isNotEmpty() && pass.isNotEmpty() && settings.positionMode == "off") {
            settings.positionMode = "smart"
        }
        if (call.isNotEmpty()) ws.setCredentials(call, pass)
        startBeaconingIfPermitted()
        // Force an immediate beacon if we can - covers the case where smart mode
        // was just enabled and the user isn't moving so no new fix is incoming.
        if (settings.positionMode == "smart" && beacon.myPosition.value != null) {
            beacon.beaconNow()
        }
    }

    fun thread(call: String) = messages.thread(call)

    fun deleteConversation(call: String) {
        viewModelScope.launch { runCatching { messages.deleteConversation(call) } }
    }

    fun send(to: String, text: String) {
        viewModelScope.launch { runCatching { messages.sendMessage(to, text) } }
    }

    fun retry(rowId: Long) {
        viewModelScope.launch { runCatching { messages.retry(rowId) } }
    }

    fun markRead(call: String) {
        viewModelScope.launch { runCatching { messages.markRead(call) } }
    }

    // --- contacts ---
    fun addContact(callsign: String, alias: String = "") {
        viewModelScope.launch {
            runCatching {
                contactDao.upsert(
                    ContactEntity(callsign = callsign.trim().uppercase(), alias = alias.trim())
                )
            }
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch { runCatching { contactDao.delete(contact) } }
    }

    override fun onCleared() {
        beacon.stop()
        ws.disconnect()
        super.onCleared()
    }
}
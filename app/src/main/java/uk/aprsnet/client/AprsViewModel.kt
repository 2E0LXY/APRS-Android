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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
import uk.aprsnet.client.net.AisWebSocket
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
    private var aisWs: AisWebSocket? = null

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

    /** Callsigns of registered APRS Net members - refreshed every 5 minutes. */
    private val _memberCallsigns = MutableStateFlow<Set<String>>(emptySet())
    val memberCallsigns: StateFlow<Set<String>> = _memberCallsigns

    init {
        viewModelScope.launch {
            ws.positionData.collect { json ->
                val call = json.optString("call")
                if (call.isNotEmpty() && json.has("lat") && json.has("lon")) {
                    val sym = json.optString("sym", "")
                    val symTable = if (sym.isNotEmpty()) sym[0] else '/'
                    val symCode = if (sym.length >= 2) sym[1] else ' '
                    val path = json.optString("path", "")
                    val type = PacketParser.classify(call, path, symTable, symCode)
                    _stations.value = _stations.value + (call to Station(
                        callsign = call,
                        lat = json.optDouble("lat"),
                        lon = json.optDouble("lon"),
                        symbolTable = symTable,
                        symbolCode = symCode,
                        comment = json.optString("raw", ""),
                        path = json.optString("path", ""),
                        raw = json.optString("raw", ""),
                        lastHeard = System.currentTimeMillis(),
                        type = type
                    ))                }
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
        // Direct AIS connection if configured
        startAis()
        // Poll member callsign list for ANUK badges (5-minute interval)
        viewModelScope.launch {
            while (true) {
                runCatching { _memberCallsigns.value = AprsApi.memberCallsigns() }
                delay(300_000)
            }
        }
    }

    /** Starts a direct aisstream.io connection if a key is configured. */
    private fun startAis() {
        val key = settings.aisApiKey
        if (key.isBlank()) return
        val aisSock = AisWebSocket(key)
        aisWs = aisSock
        viewModelScope.launch {
            aisSock.ships.collect { ship ->
                _stations.value = _stations.value + (ship.mmsi to
                    uk.aprsnet.client.model.Station(
                        callsign  = ship.mmsi,
                        lat       = ship.lat,
                        lon       = ship.lon,
                        symbolTable = '/',
                        symbolCode  = 's',
                        comment   = ship.name,
                        path      = "AIS",
                        raw       = "${ship.mmsi}>AIS:!AIS${if (ship.name.isNotEmpty()) " ${ship.name}" else ""}",
                        lastHeard = System.currentTimeMillis(),
                        type      = StationType.SHIP
                    )
                )
            }
        }
        aisSock.connect()
    }

    /** Stops any existing AIS connection and restarts if a key is present. */
    fun restartAis() {
        aisWs?.disconnect()
        aisWs = null
        startAis()
    }

    fun start(callsign: String, passcode: String) {
        val call = callsign.ifEmpty { settings.callsign }
        val pass = passcode.ifEmpty { settings.passcode }
        messages.myCallsign = settings.fullCallsign     // includes SSID suffix if set
        if (call.isNotEmpty()) ws.setCredentials(call, pass)
        ws.connect()
        startBeaconingIfPermitted()
        startSyncListeners()
        startGeoFenceSyncListener()
    }

    /**
     * Last time we performed a full server message sync. Used to rate-limit
     * sync-on-reconnect to once per 5 minutes.
     */
    private var lastServerSyncMs = 0L

    /**
     * Sync server messages and apply any member_sync prefs events.
     * Called once at startup and wired to ws.onAuthed for reconnect handling.
     */
    private fun startSyncListeners() {
        val token = settings.memberToken
        // Sync messages on every successful WS auth (login + reconnect)
        viewModelScope.launch {
            ws.onAuthed.collect {
                val t = settings.memberToken
                if (t.isNullOrEmpty()) return@collect
                val now = System.currentTimeMillis()
                if (now - lastServerSyncMs < 5 * 60 * 1000L) return@collect
                lastServerSyncMs = now
                val msgs = AprsApi.memberMessages(t)
                if (msgs.length() > 0) messages.syncFromServer(msgs)
            }
        }
        // Apply server-pushed preference changes from other devices
        viewModelScope.launch {
            ws.memberSyncPrefs.collect { prefs ->
                settings.dropPistar = prefs.optBoolean("drop_pistar", settings.dropPistar)
                settings.dropDstar  = prefs.optBoolean("drop_dstar",  settings.dropDstar)
                settings.dropApdesk = prefs.optBoolean("drop_apdesk", settings.dropApdesk)
                if (prefs.has("msg_background")) {
                    settings.messageBackgroundId = prefs.optInt("msg_background", settings.messageBackgroundId)
                }
                tickFilters()
            }
        }
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
        val effectiveCall = r.callsign.ifEmpty { callsign }.trim().uppercase()
        settings.callsign = effectiveCall
        // Prefer the server-supplied passcode. Fall back to local calculation
        // if the server returns empty/zero/'-1' (e.g. an older Member record
        // saved before the Passcode field existed, or an account where the
        // calc-on-register step didn't run). The passcode is a deterministic
        // hash of the callsign with no secrets, so calculating it locally is
        // safe and matches what the server would have computed.
        val serverPass = r.passcode
        val parsed = serverPass.trim().toIntOrNull()
        val effectivePass = when {
            parsed != null && parsed > 0 -> parsed.toString()
            effectiveCall.isNotEmpty()   ->
                uk.aprsnet.client.aprs.AprsUtils.passcode(effectiveCall).toString()
            else                         -> serverPass
        }
        settings.passcode = effectivePass
        settings.memberToken = r.token
        settings.memberName = r.name
        applySettings()
        // Pull the server-stored map filter preferences and apply them.
        // Silent on failure: local defaults remain in place if the call fails.
        viewModelScope.launch {
            val prefs = AprsApi.memberPreferences(r.token) ?: return@launch
            settings.dropPistar = prefs.optBoolean("drop_pistar", settings.dropPistar)
            settings.dropDstar  = prefs.optBoolean("drop_dstar",  settings.dropDstar)
            settings.dropApdesk = prefs.optBoolean("drop_apdesk", settings.dropApdesk)
            if (prefs.has("msg_background")) {
                settings.messageBackgroundId = prefs.optInt("msg_background", settings.messageBackgroundId)
            }
            tickFilters()
        }
        return null
    }

    /**
     * Pushes the current drop-filter SettingsStore values back to the server
     * so that the web map at aprsnet.uk sees the same preferences on next
     * login. Called from SettingsScreen whenever the user toggles a drop
     * filter. No-op if the user is not signed in to a member account.
     */
    fun pushMemberPreferences() {
        val token = settings.memberToken
        if (token.isNullOrEmpty()) return
        viewModelScope.launch {
            val prefs = org.json.JSONObject().apply {
                put("drop_pistar",     settings.dropPistar)
                put("drop_dstar",      settings.dropDstar)
                put("drop_apdesk",     settings.dropApdesk)
                put("msg_background",  settings.messageBackgroundId)
            }
            AprsApi.memberPreferencesSet(token, prefs)
        }
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
        restartAis()
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
        uk.aprsnet.client.service.NotificationHelper.clearMessage(getApplication(), call)
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

    private val _alertRules = MutableStateFlow<List<uk.aprsnet.client.model.AlertRule>>(emptyList())
    val alertRules = _alertRules.asStateFlow()

    // ── Geo-fence alert rules ──────────────────────────────────────────────────

    fun loadAlertRules() {
        val token = settings.memberToken; if (token.isNullOrEmpty()) return
        viewModelScope.launch {
            runCatching { _alertRules.value = AprsApi.getAlertRules(token) }
        }
    }

    private fun startGeoFenceSyncListener() {
        viewModelScope.launch {
            // Reload rules on every WS re-auth so the list is fresh after
            // reconnect (catches changes made on the web while offline).
            ws.onAuthed.collect { loadAlertRules() }
        }
        viewModelScope.launch {
            // Another device created/deleted a rule — server pushes the full
            // updated list via geo_fence_sync; apply it immediately.
            ws.geoFenceSync.collect { arr ->
                runCatching {
                    val updated = (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        uk.aprsnet.client.model.AlertRule(
                            id             = o.optLong("id"),
                            type           = o.optString("type"),
                            watchCallsign  = o.optString("watch_callsign", "*"),
                            lat            = o.optDouble("lat"),
                            lon            = o.optDouble("lon"),
                            radiusMi       = o.optDouble("radius_mi", 10.0),
                            name           = o.optString("name", "")
                        )
                    }
                    _alertRules.value = updated
                }
            }
        }
    }

    fun createAlertRule(rule: uk.aprsnet.client.model.AlertRule, onDone: () -> Unit) {
        val token = settings.memberToken; if (token.isNullOrEmpty()) { onDone(); return }
        viewModelScope.launch {
            runCatching {
                val created = AprsApi.createAlertRule(token, rule)
                if (created != null) _alertRules.value = _alertRules.value + created
            }
            onDone()
        }
    }

    fun deleteAlertRule(ruleId: Long) {
        val token = settings.memberToken; if (token.isNullOrEmpty()) return
        viewModelScope.launch {
            runCatching { if (AprsApi.deleteAlertRule(token, ruleId))
                _alertRules.value = _alertRules.value.filter { it.id != ruleId }
            }
        }
    }

    /**
     * Attempt to deliver a FAILED message directly via the APRS Net server.
     * Requires the user to be signed in to a member account.
     */
    /**
     * Send a new message directly via the APRS Net server, bypassing APRS-IS.
     * Only callable when the user is signed in to a member account and the
     * recipient is also a registered member. Creates the message row with
     * SERVER_SENT state on success so the bubble renders amber immediately.
     */
    fun sendDirect(to: String, text: String) {
        val token = settings.memberToken
        if (token.isNullOrEmpty()) return
        viewModelScope.launch {
            runCatching { messages.sendDirectMessage(to, text, token) }
        }
    }

    fun sendViaServer(rowId: Long) {
        val token = settings.memberToken
        if (token.isNullOrEmpty()) return
        viewModelScope.launch { messages.sendViaServer(rowId, token) }
    }

    override fun onCleared() {
        beacon.stop()
        ws.disconnect()
        aisWs?.disconnect()
        super.onCleared()
    }
}
package uk.aprsnet.client

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.aprsnet.client.aprs.PacketParser
import uk.aprsnet.client.data.AppDatabase
import uk.aprsnet.client.data.ConversationSummary
import uk.aprsnet.client.data.MessageEntity
import uk.aprsnet.client.data.MessageRepository
import uk.aprsnet.client.model.Station
import uk.aprsnet.client.model.StationType
import uk.aprsnet.client.net.AprsWebSocket

/**
 * Holds the live application state. Stage 1: WebSocket + station map.
 * Stage 2 adds messaging: a repository over a Room database, with ACK
 * matching (green bubbles), auto-ACK, retries, and an incoming-message
 * event stream the UI/service uses to raise notifications.
 */
class AprsViewModel(app: Application) : AndroidViewModel(app) {

    val ws = AprsWebSocket()

    private val db = AppDatabase.get(app)
    val messages = MessageRepository(db.messageDao(), ws)

    private val _stations = MutableStateFlow<Map<String, Station>>(emptyMap())
    val stations: StateFlow<Map<String, Station>> = _stations

    val connState: StateFlow<AprsWebSocket.ConnState> = ws.state

    /** Emitted when a message addressed to us arrives - drives notifications. */
    val incomingMessages = MutableSharedFlow<MessageEntity>(extraBufferCapacity = 32)

    val conversations = messages.conversations()
    val totalUnread = messages.totalUnread()

    init {
        // Decoded position objects -> Station map
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
        // Raw packets -> positions for the map AND messages for the repo
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
        // Retry sweep for un-ACKed outgoing messages
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                runCatching { messages.retrySweep() }
            }
        }
    }

    fun start(callsign: String, passcode: String) {
        messages.myCallsign = callsign.trim().uppercase()
        if (callsign.isNotEmpty()) ws.setCredentials(callsign, passcode)
        ws.connect()
    }

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
        ws.disconnect()
        super.onCleared()
    }
}
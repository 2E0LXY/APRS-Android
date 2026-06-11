package uk.aprsnet.client.net

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

/**
 * The single WebSocket connection to wss://www.aprsnet.uk/ws.
 * Carries everything: live station/message feed (rx), authentication,
 * and transmit (messages, ACKs, position beacons).
 *
 * Auto-reconnects with exponential backoff. Re-authenticates on reconnect.
 */
class AprsWebSocket {

    companion object {
        const val WS_URL = "wss://www.aprsnet.uk/ws"
    }

    enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED, AUTHED }

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var ws: WebSocket? = null
    private var retry = 0
    private var shouldRun = false

    private var callsign: String = ""
    private var passcode: String = ""

    /** Raw incoming packet strings (the "packet" field of rx messages). */
    val rawPackets = MutableSharedFlow<String>(extraBufferCapacity = 256)

    /** Decoded position "data" objects from rx messages, as JSON. */
    val positionData = MutableSharedFlow<JSONObject>(extraBufferCapacity = 256)

    /** Connection state for the UI. */
    val state = MutableStateFlow(ConnState.DISCONNECTED)

    /**
     * Emits a preferences JSONObject whenever the server pushes a member_sync
     * event — i.e. another device saved preferences and we should apply them.
     */
    val memberSyncPrefs = MutableSharedFlow<JSONObject>(extraBufferCapacity = 8)

    /**
     * Emits Unit each time authentication succeeds (including on reconnect).
     * Collectors use this to trigger a server-side message history sync.
     */
    val onAuthed = MutableSharedFlow<Unit>(extraBufferCapacity = 2)

    fun setCredentials(call: String, pass: String) {
        callsign = call.trim().uppercase()
        passcode = pass.trim()
        // if already connected, (re)authenticate
        if (state.value == ConnState.CONNECTED || state.value == ConnState.AUTHED) {
            authenticate()
        }
    }

    fun connect() {
        shouldRun = true
        openSocket()
    }

    fun disconnect() {
        shouldRun = false
        ws?.close(1000, "client closing")
        ws = null
        state.value = ConnState.DISCONNECTED
    }

    private fun openSocket() {
        state.value = ConnState.CONNECTING
        val req = Request.Builder().url(WS_URL).build()
        ws = client.newWebSocket(req, listener)
    }

    private fun authenticate() {
        if (callsign.isEmpty() || passcode.isEmpty()) return
        val o = JSONObject()
        o.put("type", "auth")
        o.put("callsign", callsign)
        o.put("passcode", passcode)
        o.put("software", "APRSNetAndroid 2.0")
        ws?.send(o.toString())
    }

    /** Transmit a raw APRS packet (type:tx). Requires an authed connection. */
    fun transmit(packet: String): Boolean {
        val sock = ws ?: return false
        if (state.value != ConnState.AUTHED) return false
        val o = JSONObject()
        o.put("type", "tx")
        o.put("packet", packet)
        return sock.send(o.toString())
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            retry = 0
            state.value = ConnState.CONNECTED
            authenticate()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val o = JSONObject(text)
                when (o.optString("type")) {
                    "auth_ack", "authok", "logresp" -> {
                        if (o.optString("status", "success") == "success") {
                            state.value = ConnState.AUTHED
                            onAuthed.tryEmit(Unit)
                        }
                    }
                    "rx", "obj" -> {
                        o.optString("packet").takeIf { it.isNotEmpty() }?.let {
                            rawPackets.tryEmit(it)
                        }
                        o.optJSONObject("data")?.let { positionData.tryEmit(it) }
                    }
                    "member_sync" -> {
                        // Another device updated preferences — apply them here.
                        o.optJSONObject("data")?.let { memberSyncPrefs.tryEmit(it) }
                    }
                }
            } catch (_: Exception) { /* ignore malformed */ }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (state.value == ConnState.DISCONNECTED && !shouldRun) return
        state.value = ConnState.DISCONNECTED
        if (!shouldRun) return
        retry++
        val delayMs = min(30000.0, 1000.0 * 1.5.pow(min(retry, 10))).toLong()
        Thread {
            Thread.sleep(delayMs)
            if (shouldRun) openSocket()
        }.start()
    }
}
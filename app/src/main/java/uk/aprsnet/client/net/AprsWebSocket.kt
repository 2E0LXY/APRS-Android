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
        .pingInterval(45, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val socketLock = Any()
    @Volatile
    private var ws: WebSocket? = null
    private var retry = 0
    private var reconnectGeneration = 0L
    private var reconnectScheduled = false
    @Volatile
    private var shouldRun = false

    private var callsign: String = ""
    private var passcode: String = ""
    private var clientId: String = ""

    /** Raw incoming packet strings (the "packet" field of rx messages). */
    val rawPackets = MutableSharedFlow<String>(extraBufferCapacity = 8192)

    /** Decoded position "data" objects from rx messages, as JSON. */
    val positionData = MutableSharedFlow<JSONObject>(extraBufferCapacity = 8192)

    /** Connection state for the UI. */
    val state = MutableStateFlow(ConnState.DISCONNECTED)

    /**
     * Emits a preferences JSONObject whenever the server pushes a member_sync
     * event — i.e. another device saved preferences and we should apply them.
     */
    val memberSyncPrefs = MutableSharedFlow<JSONObject>(extraBufferCapacity = 8)

    /**
     * Emits the full JSONArray of alert rules whenever another device creates
     * or deletes a geo-fence rule (server pushes geo_fence_sync).
     */
    val geoFenceSync = MutableSharedFlow<org.json.JSONArray>(extraBufferCapacity = 4)

    /**
     * Emits Unit each time authentication succeeds (including on reconnect).
     * Collectors use this to trigger a server-side message history sync.
     */
    val onAuthed = MutableSharedFlow<Unit>(extraBufferCapacity = 2)

    fun setCredentials(call: String, pass: String) {
        synchronized(socketLock) {
            callsign = call.trim().uppercase()
            passcode = pass.trim()
        }
        // Authentication is an in-band message. A connecting socket will use
        // the new values in onOpen; an open socket can re-authenticate without
        // creating another connection.
        if (state.value == ConnState.AUTHED || state.value == ConnState.CONNECTED) {
            authenticate(ws)
        }
    }

    fun setClientId(id: String) {
        synchronized(socketLock) {
            clientId = id.trim().take(128)
        }
    }

    fun connect() {
        synchronized(socketLock) {
            shouldRun = true
            if (ws == null && !reconnectScheduled) openSocketLocked()
        }
    }

    fun disconnect() {
        val closing = synchronized(socketLock) {
            shouldRun = false
            reconnectGeneration++
            reconnectScheduled = false
            val current = ws
            ws = null
            state.value = ConnState.DISCONNECTED
            current
        }
        closing?.close(1000, "client closing")
    }

    private fun openSocketLocked() {
        if (!shouldRun || ws != null) return
        reconnectGeneration++
        reconnectScheduled = false
        state.value = ConnState.CONNECTING
        val req = Request.Builder().url(WS_URL).build()
        ws = client.newWebSocket(req, listener)
    }

    private fun authenticate(socket: WebSocket?) {
        val identity = synchronized(socketLock) {
            Triple(callsign, passcode, clientId)
        }
        if (socket == null || identity.first.isEmpty() || identity.second.isEmpty()) return
        val o = JSONObject()
        o.put("type", "auth")
        o.put("callsign", identity.first)
        o.put("passcode", identity.second)
        o.put("software", "APRSNetAndroid 2.0")
        if (identity.third.isNotEmpty()) o.put("client_id", identity.third)
        socket.send(o.toString())
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
            val current = synchronized(socketLock) {
                if (webSocket !== ws) {
                    false
                } else {
                    retry = 0
                    state.value = ConnState.CONNECTED
                    true
                }
            }
            if (!current) {
                webSocket.close(1000, "superseded connection")
                return
            }
            authenticate(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== ws) return
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
                    "geo_fence_sync" -> {
                        // Another device created or deleted a geo-fence rule.
                        // The server pushes the full updated rule list; refresh.
                        val arr = o.optJSONArray("data") ?: org.json.JSONArray()
                        geoFenceSync.tryEmit(arr)
                    }
                }
            } catch (_: Exception) { /* ignore malformed */ }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            handleSocketEnd(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleSocketEnd(webSocket)
        }
    }

    private fun handleSocketEnd(ended: WebSocket) {
        val reconnect = synchronized(socketLock) {
            // OkHttp can report a terminal callback for an obsolete socket,
            // or more than one terminal callback for the same socket. Only
            // the current socket may schedule one reconnect.
            if (ended !== ws) return
            ws = null
            state.value = ConnState.DISCONNECTED
            if (!shouldRun || reconnectScheduled) return
            retry++
            reconnectScheduled = true
            val token = ++reconnectGeneration
            token to min(30000.0, 1000.0 * 1.5.pow(min(retry, 10))).toLong()
        }
        Thread {
            Thread.sleep(reconnect.second)
            synchronized(socketLock) {
                if (shouldRun &&
                    reconnect.first == reconnectGeneration &&
                    ws == null
                ) {
                    reconnectScheduled = false
                    openSocketLocked()
                }
            }
        }.start()
    }
}

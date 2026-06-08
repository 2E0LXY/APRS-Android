package uk.aprsnet.client.net

import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

/** Position data for one AIS vessel received from aisstream.io. */
data class AisShip(
    val mmsi: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val sogKnots: Float,
    val cogDeg: Float
)

/**
 * Direct WebSocket connection to wss://stream.aisstream.io/v0/stream.
 *
 * Subscribes to PositionReport messages within the UK / NW-European
 * coastal box and emits each valid vessel fix as an [AisShip].
 *
 * Reconnects automatically with exponential back-off (5s base, 2x, cap 120s).
 * Call [connect] once and [disconnect] on cleanup.  A blank [apiKey]
 * is accepted but will be rejected server-side immediately.
 */
class AisWebSocket(private val apiKey: String) {

    val ships = MutableSharedFlow<AisShip>(extraBufferCapacity = 512)

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var ws: WebSocket? = null
    private var retry = 0
    @Volatile private var shouldRun = false

    fun connect() {
        shouldRun = true
        openSocket()
    }

    fun disconnect() {
        shouldRun = false
        ws?.close(1000, null)
        ws = null
    }

    private fun openSocket() {
        val req = Request.Builder()
            .url("wss://stream.aisstream.io/v0/stream")
            .build()
        ws = client.newWebSocket(req, listener)
    }

    private fun subscribe() {
        val sub = JSONObject().apply {
            put("APIKey", apiKey)
            put("BoundingBoxes", JSONArray().apply {
                put(JSONArray().apply {                     // one bounding box
                    put(JSONArray().apply { put(48.0); put(-12.0) })  // SW corner
                    put(JSONArray().apply { put(62.0); put(5.0) })    // NE corner
                })
            })
            put("FilterMessageTypes", JSONArray().apply { put("PositionReport") })
        }
        ws?.send(sub.toString())
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            retry = 0
            subscribe()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val o = JSONObject(text)
                if (o.optString("MessageType") != "PositionReport") return
                val meta = o.optJSONObject("MetaData") ?: return
                val pr   = o.optJSONObject("Message")
                               ?.optJSONObject("PositionReport") ?: return

                val lat = pr.optDouble("Latitude",  91.0)
                val lon = pr.optDouble("Longitude", 181.0)
                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return
                if (lat == 0.0 && lon == 0.0) return

                val mmsi = meta.optString("MMSI_String").ifEmpty {
                    meta.optLong("MMSI").takeIf { it > 0 }?.toString() ?: return
                }
                val name = meta.optString("ShipName", "").trim()
                val sog  = pr.optDouble("SpeedOverGround",  0.0).toFloat()
                val cog  = pr.optDouble("CourseOverGround", 0.0).toFloat()

                ships.tryEmit(AisShip(mmsi, name, lat, lon, sog, cog))
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
            scheduleReconnect()

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
            scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (!shouldRun) return
        retry++
        val delayMs = min(120_000.0, 5_000.0 * 2.0.pow((retry - 1).coerceAtMost(5))).toLong()
        Thread {
            Thread.sleep(delayMs)
            if (shouldRun) openSocket()
        }.start()
    }
}
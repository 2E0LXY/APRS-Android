package uk.aprsnet.client.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * REST helper for the server endpoints that aren't carried over the
 * WebSocket: server status and propagation analytics.
 */
object AprsApi {

    private const val BASE = "https://www.aprsnet.uk"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Server status snapshot. */
    data class Status(
        val uptime: String,
        val packetsRx: Long,
        val upstreamConnected: Boolean,
        val stations: Int
    )

    suspend fun status(): Status? = withContext(Dispatchers.IO) {
        runCatching {
            val json = getJson("/api/status") ?: return@runCatching null
            Status(
                uptime = json.optString("uptime", "-"),
                packetsRx = json.optLong("pkts_rx", 0),
                upstreamConnected = json.optBoolean("upstream_connected", false),
                stations = json.optInt("stations", 0)
            )
        }.getOrNull()
    }

    /** Raw analytics JSON (reliability grades, longest paths, heatmap). */
    suspend fun analytics(): JSONObject? = withContext(Dispatchers.IO) {
        getJson("/api/analytics")
    }

    private fun getJson(path: String): JSONObject? {
        val req = Request.Builder().url(BASE + path).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            return runCatching { JSONObject(body) }.getOrNull()
        }
    }
}
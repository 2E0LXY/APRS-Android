package uk.aprsnet.client.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * REST helper for the server endpoints not carried over the WebSocket:
 * server status, analytics, ISS position, weather warnings, and the
 * admin config (basic-auth protected).
 */
object AprsApi {

    private const val BASE = "https://www.aprsnet.uk"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

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

    suspend fun analytics(): JSONObject? = withContext(Dispatchers.IO) {
        getJson("/api/analytics")
    }

    /** ISS position: lat/lon and (where available) altitude/velocity. */
    data class IssPosition(
        val lat: Double,
        val lon: Double,
        val altitudeKm: Double,
        val velocityKmh: Double
    )

    suspend fun issPosition(): IssPosition? = withContext(Dispatchers.IO) {
        runCatching {
            val j = getJson("/api/iss") ?: return@runCatching null
            // server returns lat/lon; tolerate a few key spellings
            val lat = j.optDouble("lat", j.optDouble("latitude", Double.NaN))
            val lon = j.optDouble("lon", j.optDouble("longitude", Double.NaN))
            if (lat.isNaN() || lon.isNaN()) return@runCatching null
            IssPosition(
                lat = lat,
                lon = lon,
                altitudeKm = j.optDouble("altitude", 408.0),
                velocityKmh = j.optDouble("velocity", 27600.0)
            )
        }.getOrNull()
    }

    /** UK Met Office severe weather warnings - list of human-readable strings. */
    suspend fun weatherWarnings(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val text = getRaw("/api/wx/warnings") ?: return@runCatching emptyList()
            val out = mutableListOf<String>()
            // tolerate either a JSON array or an object with a "warnings" array
            val trimmed = text.trim()
            val arr: JSONArray? = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") ->
                    JSONObject(trimmed).optJSONArray("warnings")
                else -> null
            }
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i)
                    if (o != null) {
                        val level = o.optString("level", o.optString("severity", ""))
                        val desc = o.optString("description",
                            o.optString("headline", o.optString("title", "Warning")))
                        out += if (level.isNotEmpty()) "$level: $desc" else desc
                    } else {
                        out += arr.optString(i)
                    }
                }
            }
            out
        }.getOrDefault(emptyList())
    }

    /** Admin config (basic-auth). Returns the raw config JSON or null. */
    suspend fun adminConfig(user: String, pass: String): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$BASE/api/config")
                    .header("Authorization", Credentials.basic(user, pass))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body?.string() ?: return@runCatching null
                    JSONObject(body)
                }
            }.getOrNull()
        }

    private fun getJson(path: String): JSONObject? {
        val body = getRaw(path) ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun getRaw(path: String): String? {
        val req = Request.Builder().url(BASE + path).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }
}
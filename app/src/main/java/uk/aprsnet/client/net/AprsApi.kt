package uk.aprsnet.client.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
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

    /** Result of a website member-account login. */
    data class MemberLogin(
        val ok: Boolean,
        val token: String,
        val callsign: String,
        val name: String,
        val passcode: String,
        val error: String? = null
    )

    /**
     * Sign in to the user's website account at www.aprsnet.uk.
     * Returns the APRS-IS passcode (so the user doesn't have to enter it
     * manually) and a session token. The user must register an account on
     * the website first - the app does not register new accounts itself.
     */
    suspend fun memberLogin(callsign: String, password: String): MemberLogin =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("callsign", callsign.trim().uppercase())
                    put("password", password)
                }.toString()
                val req = Request.Builder()
                    .url("$BASE/api/member/login")
                    .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string() ?: ""
                    val json = runCatching { JSONObject(raw) }.getOrNull()
                    if (!resp.isSuccessful) {
                        return@use MemberLogin(
                            ok = false, token = "", callsign = "", name = "",
                            passcode = "",
                            error = json?.optString("error", "Login failed")
                                ?: "Login failed (HTTP ${resp.code})"
                        )
                    }
                    if (json == null) {
                        return@use MemberLogin(
                            ok = false, token = "", callsign = "", name = "",
                            passcode = "", error = "Bad response from server"
                        )
                    }
                    MemberLogin(
                        ok = json.optBoolean("ok", true),
                        token = json.optString("token"),
                        callsign = json.optString("callsign"),
                        name = json.optString("name"),
                        passcode = json.optString("passcode")
                    )
                }
            }.getOrElse {
                MemberLogin(
                    ok = false, token = "", callsign = "", name = "",
                    passcode = "", error = it.message ?: "Network error"
                )
            }
        }

    /**
     * GET /api/member/preferences - returns the per-member map filter
     * preferences JSON, or null on auth/network error. Empty object {}
     * means no prefs set yet on the server side. Mirrors the web map's
     * loadMemberPreferences() in index.html.
     */
    suspend fun memberPreferences(token: String): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$BASE/api/member/preferences")
                    .header("X-Member-Token", token)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val raw = resp.body?.string() ?: return@runCatching null
                    JSONObject(raw)
                }
            }.getOrNull()
        }

    /**
     * PUT /api/member/preferences - replaces the server-side prefs blob
     * with the supplied JSON. Returns true on 2xx, false otherwise.
     * Called whenever the user toggles a drop filter in the app so that
     * the same preference is visible the next time they sign in on the
     * web map.
     */
    suspend fun memberPreferencesSet(token: String, prefs: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = prefs.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$BASE/api/member/preferences")
                    .header("X-Member-Token", token)
                    .put(body)
                    .build()
                client.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }


    /**
     * GET /api/members/callsigns - returns the set of registered member callsigns.
     * Public endpoint; cached 5 minutes server-side. Used to badge ANUK members
     * throughout the UI. Silently returns empty set on error.
     */
    suspend fun memberCallsigns(): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$BASE/api/members/callsigns").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptySet()
                val raw = resp.body?.string() ?: return@runCatching emptySet()
                val arr = org.json.JSONArray(raw)
                val out = mutableSetOf<String>()
                for (i in 0 until arr.length()) out += arr.getString(i).uppercase()
                out
            }
        }.getOrDefault(emptySet())
    }

    /**
     * POST /api/member/message/send - deliver a message directly to another
     * APRS Net member via the server WebSocket, bypassing APRS-IS entirely.
     * Used as a last-resort fallback when 3 APRS retries have all failed.
     * Returns true on 2xx; false if the recipient is not a member or on error.
     */
    suspend fun memberMessageSend(token: String, to: String, text: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("to",   to.trim().uppercase())
                    put("text", text.trim())
                }.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$BASE/api/member/message/send")
                    .header("X-Member-Token", token)
                    .post(body)
                    .build()
                client.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
        }

    /** Admin config (basic-auth). Returns the raw config JSON or null. */
    suspend fun adminConfig(user: String, pass: String): JSONObject? =
        adminGetObject(user, pass, "/api/config")

    /** GET /api/admin/motd -> {enabled, message, updated} */
    suspend fun adminMotdGet(user: String, pass: String): JSONObject? =
        adminGetObject(user, pass, "/api/admin/motd")

    /** POST /api/admin/motd with {enabled, message}. Returns true on 2xx. */
    suspend fun adminMotdSet(user: String, pass: String, enabled: Boolean, message: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("enabled", enabled)
                    put("message", message)
                }
                val body = payload.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$BASE/api/admin/motd")
                    .header("Authorization", Credentials.basic(user, pass))
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp -> resp.isSuccessful }
            }.getOrDefault(false)
        }

    /** GET /api/admin/members -> JSONArray of member records. */
    suspend fun adminMembers(user: String, pass: String): org.json.JSONArray? =
        adminGetArray(user, pass, "/api/admin/members")

    /** GET /api/admin/bans -> JSONArray of {callsign, reason, added_by, added}. */
    suspend fun adminBans(user: String, pass: String): org.json.JSONArray? =
        adminGetArray(user, pass, "/api/admin/bans")

    /** GET /api/admin/audit?limit=N -> JSONArray of audit entries. */
    suspend fun adminAudit(user: String, pass: String, limit: Int = 50): org.json.JSONArray? =
        adminGetArray(user, pass, "/api/admin/audit?limit=$limit")

    /** POST /api/admin/bans with {callsign, reason}. Returns true on 2xx. */
    suspend fun adminBanAdd(user: String, pass: String, callsign: String, reason: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("callsign", callsign.trim().uppercase())
                    put("reason", reason)
                }
                val body = payload.toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val req = Request.Builder()
                    .url("$BASE/api/admin/bans")
                    .header("Authorization", Credentials.basic(user, pass))
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp -> resp.isSuccessful }
            }.getOrDefault(false)
        }

    /** DELETE /api/admin/bans?callsign=X. Returns true on 2xx. */
    suspend fun adminBanRemove(user: String, pass: String, callsign: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = java.net.URLEncoder.encode(callsign.trim().uppercase(), "UTF-8")
                val req = Request.Builder()
                    .url("$BASE/api/admin/bans?callsign=$encoded")
                    .header("Authorization", Credentials.basic(user, pass))
                    .delete()
                    .build()
                client.newCall(req).execute().use { resp -> resp.isSuccessful }
            }.getOrDefault(false)
        }

    private suspend fun adminGetObject(user: String, pass: String, path: String): JSONObject? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$BASE$path")
                    .header("Authorization", Credentials.basic(user, pass))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body?.string() ?: return@runCatching null
                    JSONObject(body)
                }
            }.getOrNull()
        }

    private suspend fun adminGetArray(user: String, pass: String, path: String): org.json.JSONArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("$BASE$path")
                    .header("Authorization", Credentials.basic(user, pass))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body?.string() ?: return@runCatching null
                    org.json.JSONArray(body)
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
package uk.aprsnet.client.data

import android.content.Context

/**
 * All user-tunable settings, persisted in SharedPreferences.
 * Read by the WebSocket auth, the beacon manager, the map/stations filters,
 * and the notification helper. Edited from the Settings screen.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("aprs_settings", Context.MODE_PRIVATE)

    // -- APRS-IS credentials ------------------------------------------------
    var callsign: String
        get() = prefs.getString("callsign", "") ?: ""
        set(v) = prefs.edit().putString("callsign", v.trim().uppercase()).apply()

    var passcode: String
        get() = prefs.getString("passcode", "") ?: ""
        set(v) = prefs.edit().putString("passcode", v.trim()).apply()

    val hasCredentials: Boolean
        get() = callsign.isNotEmpty() && passcode.isNotEmpty()

    /**
     * APRS SSID 0-15. 0 = no SSID (bare callsign).
     * Convention:
     *   0 base/home   5 IGate    9 mobile/car   13 weather
     *   1-3 generic  6 satellite 10 internet    14 truck/RV
     *   4 HF gateway 7 HT        11 balloon     15 generic
     *   8 boat       12 DTMF
     */
    var ssid: Int
        get() = prefs.getInt("ssid", 0).coerceIn(0, 15)
        set(v) = prefs.edit().putInt("ssid", v.coerceIn(0, 15)).apply()

    /** Full callsign for transmission: '2E0LXY' (ssid=0) or '2E0LXY-9' (ssid>0). */
    val fullCallsign: String
        get() = if (ssid == 0) callsign else "-$ssid"

    // -- Website member account --------------------------------------------
    // Set by the member-login flow; lets the app pull the user's passcode
    // automatically from the server, and (later) sync watchlists. The user
    // must register an account at www.aprsnet.uk first.
    var memberToken: String
        get() = prefs.getString("member_token", "") ?: ""
        set(v) = prefs.edit().putString("member_token", v).apply()

    var memberName: String
        get() = prefs.getString("member_name", "") ?: ""
        set(v) = prefs.edit().putString("member_name", v).apply()

    val memberSignedIn: Boolean get() = memberToken.isNotEmpty()

    fun clearMember() {
        prefs.edit().remove("member_token").remove("member_name").apply()
    }

    // -- Position / beaconing ----------------------------------------------
    var positionMode: String
        get() = prefs.getString("position_mode", "off") ?: "off"
        set(v) = prefs.edit().putString("position_mode", v).apply()

    var beaconComment: String
        get() = prefs.getString("beacon_comment", "APRS Net Android") ?: "APRS Net Android"
        set(v) = prefs.edit().putString("beacon_comment", v).apply()

    var symbolTable: String
        get() = prefs.getString("symbol_table", "/") ?: "/"
        set(v) = prefs.edit().putString("symbol_table", v).apply()

    var symbolCode: String
        get() = prefs.getString("symbol_code", ">") ?: ">"
        set(v) = prefs.edit().putString("symbol_code", v).apply()

    // -- Map / station-list filters ----------------------------------------
    // Type filters: which station classes the map and the stations list show.
    var showHam: Boolean
        get() = prefs.getBoolean("filter_ham", true)
        set(v) = prefs.edit().putBoolean("filter_ham", v).apply()

    var showWeather: Boolean
        get() = prefs.getBoolean("filter_weather", true)
        set(v) = prefs.edit().putBoolean("filter_weather", v).apply()

    var showGlider: Boolean
        get() = prefs.getBoolean("filter_glider", true)
        set(v) = prefs.edit().putBoolean("filter_glider", v).apply()

    var showShip: Boolean
        get() = prefs.getBoolean("filter_ship", true)
        set(v) = prefs.edit().putBoolean("filter_ship", v).apply()

    var showLora: Boolean
        get() = prefs.getBoolean("filter_lora", true)
        set(v) = prefs.edit().putBoolean("filter_lora", v).apply()

    var showOther: Boolean
        get() = prefs.getBoolean("filter_other", true)
        set(v) = prefs.edit().putBoolean("filter_other", v).apply()

    // -- Notifications -----------------------------------------------------
    var notifyMessages: Boolean
        get() = prefs.getBoolean("notify_messages", true)
        set(v) = prefs.edit().putBoolean("notify_messages", v).apply()

    var notifyWeather: Boolean
        get() = prefs.getBoolean("notify_weather", false)
        set(v) = prefs.edit().putBoolean("notify_weather", v).apply()

    /** Quiet hours - if start != end, suppress sound/vibrate between them. */
    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean("quiet_enabled", false)
        set(v) = prefs.edit().putBoolean("quiet_enabled", v).apply()

    /** Hour 0..23 - quiet hours start. */
    var quietStart: Int
        get() = prefs.getInt("quiet_start", 22)
        set(v) = prefs.edit().putInt("quiet_start", v.coerceIn(0, 23)).apply()

    /** Hour 0..23 - quiet hours end. */
    var quietEnd: Int
        get() = prefs.getInt("quiet_end", 7)
        set(v) = prefs.edit().putInt("quiet_end", v.coerceIn(0, 23)).apply()

    /** Is the current local hour inside the quiet-hours window? */
    fun inQuietHours(now: java.util.Calendar = java.util.Calendar.getInstance()): Boolean {
        if (!quietHoursEnabled) return false
        val h = now.get(java.util.Calendar.HOUR_OF_DAY)
        return if (quietStart == quietEnd) false
        else if (quietStart < quietEnd) h in quietStart until quietEnd
        else h >= quietStart || h < quietEnd     // wraps midnight
    }
}
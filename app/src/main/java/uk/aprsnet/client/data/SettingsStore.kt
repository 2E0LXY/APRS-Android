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
        get() = if (ssid == 0) callsign else "$callsign-$ssid"

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

    // -- SmartBeacon tuning -------------------------------------------
    /** Hard floor: never beacon more often than this (seconds). Default 30. */
    var smartMinSec: Int
        get() = prefs.getInt("smart_min_sec", 30).coerceIn(10, 600)
        set(v) = prefs.edit().putInt("smart_min_sec", v.coerceIn(10, 600)).apply()

    /** Beacon period when slow or stationary (seconds). Default 1200 (20 min). */
    var smartSlowRateSec: Int
        get() = prefs.getInt("smart_slow_rate_sec", 1200).coerceIn(60, 3600)
        set(v) = prefs.edit().putInt("smart_slow_rate_sec", v.coerceIn(60, 3600)).apply()

    /** Beacon period when moving fast (seconds). Default 120 (2 min). */
    var smartFastRateSec: Int
        get() = prefs.getInt("smart_fast_rate_sec", 120).coerceIn(10, 600)
        set(v) = prefs.edit().putInt("smart_fast_rate_sec", v.coerceIn(10, 600)).apply()

    var beaconComment: String
        get() = prefs.getString("beacon_comment", "APRS Net Android") ?: "APRS Net Android"
        set(v) = prefs.edit().putString("beacon_comment", v).apply()

    /** Distance filter – 0 means no limit. */
    var filterRadiusKm: Int
        get() = prefs.getInt("filter_radius_km", 0)
        set(v) = prefs.edit().putInt("filter_radius_km", v).apply()

    /** APRS status text – transmitted as a separate >status packet alongside each beacon. */
    var statusText: String
        get() = prefs.getString("status_text", "") ?: ""
        set(v) = prefs.edit().putString("status_text", v.trim()).apply()

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

    /** MMDVM digital-voice hotspots (DMR / D-STAR / YSF). Matched by callsign. */
    var showMmdvm: Boolean
        get() = prefs.getBoolean("filter_mmdvm", true)
        set(v) = prefs.edit().putBoolean("filter_mmdvm", v).apply()

    // -- Per-member drop preferences (synced from server on member login) -----
    // These mirror the web-map dashboard "Map Filter Preferences" section
    // and the server-side admin Drop Filters. When set, the app filters out
    // matching packets in MapScreen and StationsScreen.

    /** Hide digital-voice gateway beacons: Pi-Star, MMDVM, APDPRS, DMRGateway, ircDDB. */
    var dropPistar: Boolean
        get() = prefs.getBoolean("drop_pistar", false)
        set(v) = prefs.edit().putBoolean("drop_pistar", v).apply()

    /** Hide D-STAR repeater forwarding traffic. */
    var dropDstar: Boolean
        get() = prefs.getBoolean("drop_dstar", false)
        set(v) = prefs.edit().putBoolean("drop_dstar", v).apply()

    /** Hide APDESK (UI-View desktop client) status beacons. */
    var dropApdesk: Boolean
        get() = prefs.getBoolean("drop_apdesk", false)
        set(v) = prefs.edit().putBoolean("drop_apdesk", v).apply()

    /**
     * One-shot flag: true once we have presented the post-install setup
     * dialog (battery exemption + pin-to-home-screen). Suppresses the
     * dialog from popping up again on every cold start.
     */
    var hasShownSetup: Boolean
        get() = prefs.getBoolean("has_shown_setup", false)
        set(v) = prefs.edit().putBoolean("has_shown_setup", v).apply()

    /**
     * Which painterly background to use behind the Messages section.
     * 0..6 - see MessageBackground.kt for the palette / pattern
     * keyed to each index. Default 0 = Dark Teal.
     */
    var messageBackgroundId: Int
        get() = prefs.getInt("message_background_id", 0)
        set(v) = prefs.edit().putInt("message_background_id", v.coerceIn(0, 6)).apply()

    // -- AIS (direct aisstream.io) -----------------------------------------
    /**
     * Optional aisstream.io API key for a direct AIS vessel feed.
     * Empty string (default) disables the direct connection; AIS data then
     * comes from the server relay only (if the server is configured).
     * Free tier allows one WebSocket connection per key - do NOT use the
     * same key as the server.
     */
    var aisApiKey: String
        get() = prefs.getString("ais_api_key", "") ?: ""
        set(v) = prefs.edit().putString("ais_api_key", v.trim()).apply()

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

    // -- Appearance --------------------------------------------------------
    /**
     * Theme id: 0 = default navy, 1 = sunset, 2 = forest, 3 = aurora, 4 = mono.
     * Affects the page background gradient and accent tints.
     */
    var themeId: Int
        get() = prefs.getInt("theme_id", 0).coerceIn(0, 4)
        set(v) = prefs.edit().putInt("theme_id", v.coerceIn(0, 4)).apply()

    /**
     * Bubble colour id for outgoing messages: 0 = cyan->navy (default),
     * 1 = amber, 2 = lime, 3 = purple, 4 = rose, 5 = blue.
     * ACKed bubbles always show lime->green so success stays unambiguous.
     */
    var bubbleColourId: Int
        get() = prefs.getInt("bubble_colour_id", 0).coerceIn(0, 5)
        set(v) = prefs.edit().putInt("bubble_colour_id", v.coerceIn(0, 5)).apply()

    /** Incoming-bubble palette id (0..5). Default 0 (panel-elevated). */
    var incomingBubbleColourId: Int
        get() = prefs.getInt("incoming_bubble_colour_id", 0).coerceIn(0, 5)
        set(v) = prefs.edit().putInt("incoming_bubble_colour_id", v.coerceIn(0, 5)).apply()

    /** Is the current local hour inside the quiet-hours window? */
    fun inQuietHours(now: java.util.Calendar = java.util.Calendar.getInstance()): Boolean {
        if (!quietHoursEnabled) return false
        val h = now.get(java.util.Calendar.HOUR_OF_DAY)
        return if (quietStart == quietEnd) false
        else if (quietStart < quietEnd) h in quietStart until quietEnd
        else h >= quietStart || h < quietEnd     // wraps midnight
    }
}

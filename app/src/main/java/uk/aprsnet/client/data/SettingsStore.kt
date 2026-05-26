package uk.aprsnet.client.data

import android.content.Context

/**
 * Simple persisted settings (SharedPreferences). The dedicated Settings
 * screen arrives in Stage 5; for now this is the single source of truth
 * for the callsign/passcode and beaconing preferences that Stage 3 needs.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("aprs_settings", Context.MODE_PRIVATE)

    var callsign: String
        get() = prefs.getString("callsign", "") ?: ""
        set(v) = prefs.edit().putString("callsign", v.trim().uppercase()).apply()

    var passcode: String
        get() = prefs.getString("passcode", "") ?: ""
        set(v) = prefs.edit().putString("passcode", v.trim()).apply()

    /** Position mode: "smart", "manual", or "off". */
    var positionMode: String
        get() = prefs.getString("position_mode", "off") ?: "off"
        set(v) = prefs.edit().putString("position_mode", v).apply()

    var beaconComment: String
        get() = prefs.getString("beacon_comment", "APRS Net Android") ?: "APRS Net Android"
        set(v) = prefs.edit().putString("beacon_comment", v).apply()

    /** APRS symbol table char + code char for the user's own beacon. */
    var symbolTable: String
        get() = prefs.getString("symbol_table", "/") ?: "/"
        set(v) = prefs.edit().putString("symbol_table", v).apply()

    var symbolCode: String
        get() = prefs.getString("symbol_code", ">") ?: ">"
        set(v) = prefs.edit().putString("symbol_code", v).apply()

    var manualLat: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong("manual_lat", 0L)
        )
        set(v) = prefs.edit().putLong("manual_lat", java.lang.Double.doubleToLongBits(v)).apply()

    var manualLon: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong("manual_lon", 0L)
        )
        set(v) = prefs.edit().putLong("manual_lon", java.lang.Double.doubleToLongBits(v)).apply()

    val hasCredentials: Boolean
        get() = callsign.isNotEmpty() && passcode.isNotEmpty()
}
package uk.aprsnet.client.auto

import androidx.lifecycle.MutableLiveData

/**
 * Singleton bus between the main app and all Car App Library screens.
 * Push data from your ViewModel/Repository layer into these LiveData fields.
 * Wire the lambda callbacks in Application.onCreate or your main service.
 */
object AutoDataBridge {

    // ─── Data models ─────────────────────────────────────────────────────────

    data class StationData(
        val callsign: String,
        val lat: Double,
        val lon: Double,
        val comment: String,
        val symbol: String,
        val lastHeardMs: Long,
        val course: Int = 0,
        val speed: Float = 0f,      // knots
        val altitude: Float = 0f,   // metres
        val path: String = ""
    )

    data class PositionData(
        val lat: Double,
        val lon: Double,
        val course: Int = 0,
        val speed: Float = 0f       // knots
    )

    data class MessageData(
        val id: String,
        val from: String,
        val to: String,
        val body: String,
        val timestampMs: Long,
        val acked: Boolean
    )

    data class WeatherData(
        val callsign: String,
        val tempC: Float?,
        val windSpeedKts: Float?,
        val windDirDeg: Int?,
        val rainMmLastHour: Float?,
        val humidity: Int?,
        val pressureMb: Float?,
        val timestampMs: Long
    )

    data class AlertData(
        val id: String,
        val title: String,
        val body: String,
        val timestampMs: Long
    )

    // ─── Live feeds — write from main app ───────────────────────────────────

    val stations       = MutableLiveData<List<StationData>>(emptyList())
    val myPosition     = MutableLiveData<PositionData?>()
    val messages       = MutableLiveData<List<MessageData>>(emptyList())
    val weather        = MutableLiveData<List<WeatherData>>(emptyList())
    val alerts         = MutableLiveData<List<AlertData>>(emptyList())
    val connectionStatus = MutableLiveData("Disconnected")
    val isBeaconing    = MutableLiveData(false)
    val lastBeaconMs   = MutableLiveData(0L)

    // ─── Config ──────────────────────────────────────────────────────────────

    var myCallsign: String = ""

    // ─── Callbacks — implement in main app ───────────────────────────────────

    /** Called when user sends a message via Auto UI */
    var onSendMessage: ((to: String, body: String) -> Unit)? = null

    /** Called when user taps "Beacon Now" in Auto UI */
    var onBeaconNow: (() -> Unit)? = null
}

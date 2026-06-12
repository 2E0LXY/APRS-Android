package uk.aprsnet.client.model

/**
 * Decoded APRS weather-report data block (DTI '_' or symbol code '_').
 * Wind speed/gust are mph per the APRS spec (not knots, unlike position
 * course/speed). Pressure is hPa. Humidity 100 represents the spec's "00"
 * encoding (100%). Solar/UV/lightning come from the optional comment suffix
 * (L###, UV#, LS#) used by Ecowitt and similar consumer stations.
 */
data class WxData(
    val windDirDeg: Int? = null,
    val windSpeedMph: Double? = null,
    val gustMph: Double? = null,
    val tempF: Double? = null,
    val rain1hIn: Double? = null,
    val rain24hIn: Double? = null,
    val rainDailyIn: Double? = null,
    val humidityPct: Int? = null,
    val pressureHpa: Double? = null,
    val solarWm2: Int? = null,
    val uvIndex: Int? = null,
    val lightningCount: Int? = null
)

/** A station heard on the APRS network. */
data class Station(
    val callsign: String,
    val lat: Double,
    val lon: Double,
    val symbolTable: Char = '/',
    val symbolCode: Char = '-',
    val comment: String = "",
    val course: Int? = null,
    val speedKmh: Double? = null,
    val altitudeM: Double? = null,
    val path: String = "",
    val raw: String = "",
    val lastHeard: Long = System.currentTimeMillis(),
    val type: StationType = StationType.HAM,
    val wx: WxData? = null
)

enum class StationType { HAM, WEATHER, GLIDER, OBJECT, SHIP, LORA, MMDVM, OTHER }

/** An APRS text message, incoming or outgoing. */
data class Message(
    val id: Long = 0,
    val remoteCall: String,        // the other party
    val text: String,
    val outgoing: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val aprsMsgId: String? = null, // the {NN id used for ACK matching
    val state: MessageState = MessageState.SENT
)

/** Delivery state of an outgoing message - drives the chat bubble colour. */
enum class MessageState {
    SENDING,     // queued, not yet sent
    SENT,        // transmitted to the server (single tick)
    ACKED,       // ACK received - bubble turns GREEN (double tick)
    FAILED,      // no ACK after 3 attempts
    SERVER_SENT  // delivered via APRS Net server (direct, not APRS-IS)
}

/** A saved contact. */
data class Contact(
    val callsign: String,
    val alias: String = "",
    val notes: String = ""
)

data class AlertRule(
    val id:            Long   = 0,
    val type:          String = "geofence_enter",  // "geofence_enter" | "geofence_exit"
    val watchCallsign: String = "*",
    val lat:           Double = 0.0,
    val lon:           Double = 0.0,
    val radiusMi:      Double = 10.0,
    val name:          String = ""
)

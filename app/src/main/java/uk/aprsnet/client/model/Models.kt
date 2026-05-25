package uk.aprsnet.client.model

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
    val type: StationType = StationType.HAM
)

enum class StationType { HAM, WEATHER, GLIDER, OBJECT, SHIP, LORA, OTHER }

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
    SENDING,    // queued, not yet sent
    SENT,       // transmitted to the server (single tick)
    ACKED,      // ACK received - bubble turns GREEN (double tick)
    FAILED      // no ACK after retries
}

/** A saved contact. */
data class Contact(
    val callsign: String,
    val alias: String = "",
    val notes: String = ""
)
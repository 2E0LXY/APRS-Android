package uk.aprsnet.client.aprs

import kotlin.math.abs
import kotlin.math.floor

/**
 * Builds raw APRS packets for transmission over the WebSocket (type:tx).
 */
object PacketBuilder {

    /** A text message:  CALL>APRS,TCPIP*::DEST_____:text{NN  */
    fun message(from: String, to: String, text: String, msgId: String): String {
        val dest = to.uppercase().padEnd(9, ' ').substring(0, 9)
        return "${from.uppercase()}>APRS,TCPIP*::$dest:$text{$msgId"
    }

    /** An ACK:  CALL>APRS,TCPIP*::SENDER___:ackNN  */
    fun ack(from: String, sender: String, msgId: String): String {
        val dest = sender.uppercase().padEnd(9, ' ').substring(0, 9)
        return "${from.uppercase()}>APRS,TCPIP*::$dest:ack$msgId"
    }

    /**
     * A position beacon (position-with-messaging '=' indicator):
     *   CALL>APRS,TCPIP*:=DDMM.hhN/DDDMM.hhW>comment
     */
    fun position(
        from: String,
        lat: Double,
        lon: Double,
        symbolTable: Char = '/',
        symbolCode: Char = '>',
        comment: String = ""
    ): String {
        return "${from.uppercase()}>APRS,TCPIP*:=" +
            formatLat(lat) + symbolTable + formatLon(lon) + symbolCode + comment
    }

    private fun formatLat(lat: Double): String {
        val hemi = if (lat >= 0) 'N' else 'S'
        val a = abs(lat)
        val deg = floor(a).toInt()
        val min = (a - deg) * 60.0
        return "%02d%05.2f%c".format(deg, min, hemi)
    }

    private fun formatLon(lon: Double): String {
        val hemi = if (lon >= 0) 'E' else 'W'
        val a = abs(lon)
        val deg = floor(a).toInt()
        val min = (a - deg) * 60.0
        return "%03d%05.2f%c".format(deg, min, hemi)
    }
}
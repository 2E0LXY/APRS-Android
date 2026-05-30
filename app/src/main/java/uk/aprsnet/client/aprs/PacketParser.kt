package uk.aprsnet.client.aprs

import uk.aprsnet.client.model.Message
import uk.aprsnet.client.model.MessageState
import uk.aprsnet.client.model.Station
import uk.aprsnet.client.model.StationType
import kotlin.math.pow

/**
 * Parses raw APRS packets into model objects.
 * Reference: the website's JavaScript parser. Handles message packets and
 * the common position packet formats (uncompressed + compressed).
 */
object PacketParser {

    /** Result of parsing one raw packet - either a position, a message, or null. */
    sealed class Parsed {
        data class Pos(val station: Station) : Parsed()
        data class Msg(
            val from: String,
            val to: String,
            val text: String,
            val msgId: String?,
            val isAck: Boolean,
            val ackId: String?
        ) : Parsed()
        data object Unknown : Parsed()
    }

    private val MSG_RE =
        Regex("""^([A-Z0-9\-]+)>[^:]*::([A-Z0-9 \-]{9}):(.*)$""")

    fun parse(raw: String): Parsed = runCatching {
        parseInternal(raw)
    }.getOrElse { Parsed.Unknown }

    private fun parseInternal(raw: String): Parsed {
        // --- message packet? ---
        val mm = MSG_RE.find(raw)
        if (mm != null) {
            val from = mm.groupValues[1]
            val to = mm.groupValues[2].trim()
            var body = mm.groupValues[3]

            // ACK / reject
            val ackM = Regex("""^ack([0-9A-Za-z]+)""").find(body)
            if (ackM != null) {
                return Parsed.Msg(from, to, "", null, true, ackM.groupValues[1])
            }
            val rejM = Regex("""^rej([0-9A-Za-z]+)""").find(body)
            if (rejM != null) {
                return Parsed.Msg(from, to, "", null, true, rejM.groupValues[1])
            }

            // trailing {NN message id
            var msgId: String? = null
            val idM = Regex("""\{([0-9A-Za-z]+)\}?\s*$""").find(body)
            if (idM != null) {
                msgId = idM.groupValues[1]
                body = body.replace(Regex("""\{[0-9A-Za-z]+\}?\s*$"""), "")
            }
            return Parsed.Msg(from, to, body, msgId, false, null)
        }

        // --- position packet? ---
        val gt = raw.indexOf('>')
        val colon = raw.indexOf(':')
        if (gt < 0 || colon < 0 || colon < gt) return Parsed.Unknown
        val call = raw.substring(0, gt)
        val path = raw.substring(gt + 1, colon)
        val info = raw.substring(colon + 1)
        if (info.isEmpty()) return Parsed.Unknown

        val station = parsePosition(call, path, info, raw) ?: return Parsed.Unknown
        return Parsed.Pos(station)
    }

    /** Parse a position info field. Returns null if it isn't a position packet. */
    private fun parsePosition(call: String, path: String, info: String, raw: String): Station? {
        var body = info
        val lead = body[0]
        if (lead !in charArrayOf('!', '=', '/', '@', '\'', '`')) return null

        // strip the data-type char
        body = body.substring(1)

        // packets with @ or / carry a timestamp first (7 chars) - skip it
        if (lead == '@' || lead == '/') {
            if (body.length >= 7) body = body.substring(7)
        }
        if (body.length < 10) return null

        // uncompressed:  DDMM.hhN/DDDMM.hhW&
        val uncompressed = Regex(
            """^(\d{2})(\d{2}\.\d{2})([NS])(.)(\d{3})(\d{2}\.\d{2})([EW])(.)"""
        ).find(body)

        var lat: Double
        var lon: Double
        var symTable: Char
        var symCode: Char
        var rest: String

        if (uncompressed != null) {
            val g = uncompressed.groupValues
            lat = g[1].toDouble() + g[2].toDouble() / 60.0
            if (g[3] == "S") lat = -lat
            lon = g[5].toDouble() + g[6].toDouble() / 60.0
            if (g[7] == "W") lon = -lon
            symTable = g[4][0]
            symCode = g[8][0]
            rest = body.substring(uncompressed.value.length)
        } else {
            // compressed position: symTable + 4 lat + 4 lon + symCode + 3
            if (body.length < 13) return null
            symTable = body[0]
            val latC = body.substring(1, 5)
            val lonC = body.substring(5, 9)
            symCode = body[9]
            lat = 90.0 - decodeBase91(latC) / 380926.0
            lon = -180.0 + decodeBase91(lonC) / 190463.0
            rest = if (body.length > 13) body.substring(13) else ""
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        }

        // course/speed:  ccc/sss
        var course: Int? = null
        var speed: Double? = null
        val csM = Regex("""^(\d{3})/(\d{3})""").find(rest)
        if (csM != null) {
            course = csM.groupValues[1].toIntOrNull()
            val knots = csM.groupValues[2].toDoubleOrNull()
            if (knots != null) speed = knots * 1.852
            rest = rest.substring(7)
        }

        return Station(
            callsign = call,
            lat = lat,
            lon = lon,
            symbolTable = symTable,
            symbolCode = symCode,
            comment = rest.trim(),
            course = course,
            speedKmh = speed,
            path = path,
            raw = raw,
            type = classify(call, symTable, symCode)
        )
    }

    private fun decodeBase91(s: String): Double {
        var v = 0.0
        for (c in s) v = v * 91.0 + (c.code - 33)
        return v
    }

    private fun classify(call: String, table: Char, code: Char): StationType {
        val up = call.uppercase()
        return when {
            code == '_' -> StationType.WEATHER
            code == '\'' || code == 'g' -> StationType.GLIDER
            code == 's' || code == 'Y' -> StationType.SHIP
            up.contains("LORA") -> StationType.LORA
            else -> StationType.HAM
        }
    }

    /** Build a Message model from a parsed incoming message. */
    fun messageFrom(parsed: Parsed.Msg, myCall: String): Message? {
        if (parsed.isAck) return null
        return Message(
            remoteCall = parsed.from,
            text = parsed.text,
            outgoing = false,
            aprsMsgId = parsed.msgId,
            state = MessageState.SENT
        )
    }
}
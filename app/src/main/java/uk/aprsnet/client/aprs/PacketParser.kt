package uk.aprsnet.client.aprs

import uk.aprsnet.client.model.Message
import uk.aprsnet.client.model.MessageState
import uk.aprsnet.client.model.Station
import uk.aprsnet.client.model.StationType
import uk.aprsnet.client.model.WxData
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
        val lead = info[0]
        if (lead !in charArrayOf('!', '=', '/', '@', '\'', '`')) return null

        // Mic-E: delegate to dedicated decoder
        if (lead == '`' || lead == '\'') return parseMicE(call, path, info, raw)

        var body = info.substring(1)   // strip data-type char

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

        // course/speed:  ccc/sss - NOT for weather packets, where the same
        // "CCC/SSS" position holds wind direction/speed instead.
        var course: Int? = null
        var speed: Double? = null
        var wx: WxData? = null
        if (symCode == '_') {
            wx = parseWx(rest)
            if (wx != null) rest = stripWx(rest)
        } else {
            val csM = Regex("""^(\d{3})/(\d{3})""").find(rest)
            if (csM != null) {
                course = csM.groupValues[1].toIntOrNull()
                val knots = csM.groupValues[2].toDoubleOrNull()
                if (knots != null) speed = knots * 1.852
                rest = rest.substring(7)
            }
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
            type = classify(call, path, symTable, symCode),
            wx = wx
        )
    }

    // Standard APRS weather data block:
    //   CCC/SSS[gGGG]tTTT[rRRR][pPPP][PPPP][hHH][bBBBBB]
    // wind dir / wind speed (mph) / [gust mph] / temp F / [rain 1h] /
    // [rain since midnight] / [rain 24h] / [humidity %, 00=100] / [pressure 0.1hPa]
    private val WX_RE = Regex(
        """^(\d{3})/(\d{3})(?:g(\d{3}))?t(-?\d{1,3})(?:r(\d{3}))?(?:p(\d{3}))?(?:P(\d{3}))?(?:h(\d{2}))?(?:b(\d{5}))?"""
    )
    private val L_RE  = Regex("""\bL(\d{1,4})\b""")
    private val UV_RE = Regex("""\bUV(\d{1,2})\b""")
    private val LS_RE = Regex("""\bLS(\d{1,4})\b""")

    /** Parses the WX data block + optional L###/UV#/LS# comment suffixes. */
    private fun parseWx(rest: String): WxData? {
        val m = WX_RE.find(rest) ?: return null
        val g = m.groupValues
        val windDir   = g[1].toIntOrNull()
        val windSpeed = g[2].toIntOrNull()?.toDouble()
        val gust      = g[3].toIntOrNull()?.toDouble()
        val temp      = g[4].toIntOrNull()?.toDouble()
        val rain1h    = g[5].toIntOrNull()?.let { it / 100.0 }
        val rainSince = g[6].toIntOrNull()?.let { it / 100.0 } // since midnight
        val rain24h   = g[7].toIntOrNull()?.let { it / 100.0 }
        val humidity  = g[8].toIntOrNull()?.let { if (it == 0) 100 else it }
        val pressure  = g[9].toIntOrNull()?.let { it / 10.0 }

        val solar     = L_RE.find(rest)?.groupValues?.get(1)?.toIntOrNull()
        val uv        = UV_RE.find(rest)?.groupValues?.get(1)?.toIntOrNull()
        val lightning = LS_RE.find(rest)?.groupValues?.get(1)?.toIntOrNull()

        return WxData(
            windDirDeg = windDir,
            windSpeedMph = windSpeed,
            gustMph = gust,
            tempF = temp,
            rain1hIn = rain1h,
            rain24hIn = rain24h ?: rainSince,
            rainDailyIn = rainSince,
            humidityPct = humidity,
            pressureHpa = pressure,
            solarWm2 = solar,
            uvIndex = uv,
            lightningCount = lightning
        )
    }

    /** Removes the WX data block and L###/UV#/LS# tokens, leaving free text. */
    private fun stripWx(rest: String): String {
        var out = WX_RE.replaceFirst(rest, "")
        out = L_RE.replace(out, "")
        out = UV_RE.replace(out, "")
        out = LS_RE.replace(out, "")
        return out.replace(Regex("""\s+"""), " ").trim()
    }

    private fun decodeBase91(s: String): Double {
        var v = 0.0
        for (c in s) v = v * 91.0 + (c.code - 33)
        return v
    }

    internal fun classify(call: String, path: String, table: Char, code: Char): StationType {
        val up     = call.uppercase()
        val tocall = path.substringBefore(',').uppercase()
        return when {
            // TOCALL-based LoRa detection (OE5BPA / CA2RXU firmware families).
            // Takes precedence - a normal ham callsign with APLRG* TOCALL is
            // unambiguously a LoRa iGate regardless of callsign content.
            tocall.startsWith("APLR") || tocall.startsWith("APLG") ||
                tocall.startsWith("APLT") || tocall.startsWith("APLO") ||
                (tocall.startsWith("APL") && tocall.length >= 5) -> StationType.LORA
            // TOCALL-based MMDVM/DMR detection
            tocall.startsWith("APZDMR") || tocall.startsWith("APDG") -> StationType.MMDVM
            // TOCALL-based OGN receiver detection
            tocall.startsWith("APOG") -> StationType.GLIDER
            // Fallback: callsign-string and symbol heuristics
            up.contains("MMDVM") || up.contains("PISTAR") ||
                (table == '\\' && code == 'M') -> StationType.MMDVM
            code == '_' || (code == 'W' && table == '\\') -> StationType.WEATHER
            code == '\'' || code == 'g' || code == '^' ||
                up.startsWith("OGN") -> StationType.GLIDER
            code == 's' || code == 'Y' || code == 'C' ||
                (table == '\\' && code == 's') ||
                up.matches(Regex("[0-9]{6,9}.*")) -> StationType.SHIP
            up.contains("LORA") || up.contains("MESH") -> StationType.LORA
            code == 'r' || code == '#' || code == '&' || code == 'I' -> StationType.OBJECT
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

    /**
     * Mic-E position decode. Called when info field starts with '`' or '\'' — both
     * data-type indicators for Mic-E packets. Extracts lat/lon, speed (km/h), heading,
     * and altitude (metres, from base-91 comment suffix) so BLE-received RT-950 Pro
     * packets display identically to server-decoded position data.
     *
     * Algorithm: APRS 1.0.1 spec §10.
     */
    private fun parseMicE(from: String, path: String, info: String, raw: String): Station? {
        if (info.length < 9) return null
        // Destination callsign (without SSID) encodes lat + flags
        val dest = path.substringBefore(',').substringBefore('-').uppercase()
        if (dest.length < 6) return null

        fun digit(c: Char): Int = when {
            c in '0'..'9' -> c - '0'
            c in 'A'..'J' -> c - 'A'
            c in 'P'..'Y' -> c - 'P'
            else           -> 0      // K, L, Z = ambiguous → 0
        }

        // Latitude
        val latDeg = digit(dest[0]) * 10 + digit(dest[1])
        val latMin = digit(dest[2]) * 10.0 + digit(dest[3]) + digit(dest[4]) / 10.0 + digit(dest[5]) / 100.0
        var lat = latDeg + latMin / 60.0
        if (dest[3] in 'P'..'Z') lat = -lat                       // South

        // Longitude offset and W/E from dest[4] and dest[5]
        val lonOffset = if (dest[4] in 'P'..'Z') 100 else 0
        val isWest    = dest[5] in 'P'..'Z'

        // Longitude from info bytes 1–3 (byte 0 = data-type indicator)
        var lonDeg = info[1].code - 28
        if (lonDeg >= 180) lonDeg -= 80
        lonDeg += lonOffset

        var lonMin = info[2].code - 28
        if (lonMin >= 60) lonMin -= 60
        val lonFrac = (info[3].code - 28).toDouble() / 100.0

        var lon = lonDeg + (lonMin + lonFrac) / 60.0
        if (isWest) lon = -lon

        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null

        // Speed/course from info bytes 4–6
        val spB = info[4].code - 28
        val dcB = info[5].code - 28
        val seB = info[6].code - 28
        var spd = spB * 10 + dcB / 10
        var hdg = (dcB % 10) * 100 + seB
        if (spd >= 800) spd -= 800
        if (hdg >= 400) hdg -= 400

        // Symbol: info[7] = code, info[8] = table
        val symCode  = if (info.length > 7) info[7] else '>'
        val symTable = if (info.length > 8) info[8] else '/'

        // Altitude: base-91 triplet immediately before '}' in comment
        val comment = if (info.length > 9) info.substring(9) else ""
        var altM: Double? = null
        val braceIdx = comment.indexOf('}')
        if (braceIdx >= 3) {
            val a = comment.substring(braceIdx - 3, braceIdx)
            if (a.all { it.code in 33..122 }) {
                altM = (a[0].code - 33) * 91.0 * 91.0 +
                       (a[1].code - 33) * 91.0 +
                       (a[2].code - 33) - 10000.0
            }
        }
        val cleanComment = comment.replace(Regex("""[!-{]{3}}"""), "").trim()

        return Station(
            callsign    = from,
            lat         = lat,
            lon         = lon,
            symbolTable = symTable,
            symbolCode  = symCode,
            comment     = cleanComment,
            course      = if (hdg > 0) hdg else null,
            speedKmh    = if (spd > 0) spd * 1.852 else null,
            altitudeM   = altM,
            path        = path,
            raw         = raw,
            lastHeard   = System.currentTimeMillis(),
            type        = classify(from, path, symTable, symCode)
        )
    }

}

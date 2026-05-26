package uk.aprsnet.client.aprs

import kotlin.math.floor

/**
 * Small self-contained APRS utility calculations - the native equivalents of
 * the website's Utilities tools.
 */
object AprsUtils {

    /**
     * The standard APRS-IS passcode algorithm. The passcode is derived
     * from the callsign (the part before any -SSID).
     */
    fun passcode(callsign: String): Int {
        val call = callsign.trim().uppercase().substringBefore('-')
        if (call.isEmpty()) return 0
        var hash = 0x73E2
        var i = 0
        while (i < call.length) {
            hash = hash xor (call[i].code shl 8)
            if (i + 1 < call.length) {
                hash = hash xor call[i + 1].code
            }
            i += 2
        }
        return hash and 0x7FFF
    }

    /** Convert latitude/longitude to a Maidenhead grid locator. */
    fun toMaidenhead(lat: Double, lon: Double, pairs: Int = 3): String {
        var aLon = lon + 180.0
        var aLat = lat + 90.0
        val sb = StringBuilder()

        // field
        sb.append(('A' + floor(aLon / 20.0).toInt()))
        sb.append(('A' + floor(aLat / 10.0).toInt()))
        aLon %= 20.0; aLat %= 10.0
        // square
        sb.append(('0' + floor(aLon / 2.0).toInt()))
        sb.append(('0' + floor(aLat / 1.0).toInt()))
        if (pairs >= 3) {
            aLon %= 2.0; aLat %= 1.0
            sb.append(('a' + floor(aLon / (2.0 / 24.0)).toInt()))
            sb.append(('a' + floor(aLat / (1.0 / 24.0)).toInt()))
        }
        return sb.toString()
    }

    /** Convert a Maidenhead grid locator to its centre latitude/longitude. */
    fun fromMaidenhead(grid: String): Pair<Double, Double>? {
        val g = grid.trim()
        if (g.length < 4) return null
        return runCatching {
            var lon = (g[0].uppercaseChar() - 'A') * 20.0 - 180.0
            var lat = (g[1].uppercaseChar() - 'A') * 10.0 - 90.0
            lon += (g[2] - '0') * 2.0
            lat += (g[3] - '0') * 1.0
            if (g.length >= 6) {
                lon += (g[4].lowercaseChar() - 'a') * (2.0 / 24.0)
                lat += (g[5].lowercaseChar() - 'a') * (1.0 / 24.0)
                lon += (2.0 / 24.0) / 2.0
                lat += (1.0 / 24.0) / 2.0
            } else {
                lon += 1.0
                lat += 0.5
            }
            lat to lon
        }.getOrNull()
    }
}
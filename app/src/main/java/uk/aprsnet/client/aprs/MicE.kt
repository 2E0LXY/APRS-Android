package uk.aprsnet.client.aprs

/**
 * Mic-E position decoder (APRS 1.0.1 chapter 10). Pure Kotlin, no Android dependencies, so it is
 * unit-tested on the JVM (see MicETest, vectors cross-checked against the aprslib decoder).
 *
 * Position is split between the destination address (latitude digits + N/S, E/W and the +100
 * degree longitude offset flags) and the information field (longitude, speed/course, symbol).
 */
object MicE {

    data class Result(
        val lat: Double,
        val lon: Double,
        val symbolTable: Char,
        val symbolCode: Char,
        val speedKnots: Int,
        val courseDeg: Int
    )

    /**
     * @param dest destination address (first element of the AX.25 path; SSID ignored)
     * @param info full information field including the leading '`' / '\'' data type indicator
     */
    fun decode(dest: String, info: String): Result? {
        val d = dest.substringBefore('-')
        if (d.length < 6 || info.length < 9 || (info[0] != '`' && info[0] != '\'')) return null

        // Destination chars 0-5: latitude digits plus flags.
        // 0-9 digit | A-J digit + custom msg bit | K/L space | P-Y digit + std msg bit | Z space
        val digit = IntArray(6)
        val high = BooleanArray(6)       // P-Z: the "1" flag state (msg bit / North / +100 lon / West)
        for (i in 0 until 6) {
            val c = d[i]
            when {
                c in '0'..'9' -> digit[i] = c - '0'
                c in 'A'..'J' && i < 3 -> { digit[i] = c - 'A'; high[i] = true }
                c == 'K' && i < 3 -> { digit[i] = 0; high[i] = true }
                c == 'L' -> digit[i] = 0
                c in 'P'..'Y' -> { digit[i] = c - 'P'; high[i] = true }
                c == 'Z' -> { digit[i] = 0; high[i] = true }
                else -> return null
            }
        }

        val latDeg = digit[0] * 10 + digit[1]
        val latMin = (digit[2] * 10 + digit[3]) + (digit[4] * 10 + digit[5]) / 100.0
        if (latDeg > 90 || latMin >= 60.0) return null
        var lat = latDeg + latMin / 60.0
        if (!high[3]) lat = -lat                  // char 4: 0-9/L = South, P-Z = North
        val lonOffset = if (high[4]) 100 else 0   // char 5: P-Z = +100 degrees
        val west = high[5]                        // char 6: P-Z = West

        // Info bytes 1-3 longitude, 4-6 speed/course, 7 symbol code, 8 symbol table.
        for (i in 1..8) if (info[i].code < 28) return null
        var lonDeg = info[1].code - 28 + lonOffset
        if (lonDeg >= 190) lonDeg -= 190 else if (lonDeg >= 180) lonDeg -= 80
        var lonMin = info[2].code - 28
        if (lonMin >= 60) lonMin -= 60
        val lonHun = info[3].code - 28
        if (lonDeg > 180 || lonMin !in 0..59 || lonHun !in 0..99) return null
        var lon = lonDeg + (lonMin + lonHun / 100.0) / 60.0
        if (west) lon = -lon
        if (lon > 180.0 || lon < -180.0) return null

        var speed = (info[4].code - 28) * 10 + (info[5].code - 28) / 10
        var course = ((info[5].code - 28) % 10) * 100 + (info[6].code - 28)
        if (speed >= 800) speed -= 800
        if (course >= 400) course -= 400

        val code = info[7]
        if (code.code !in 33..126) return null
        return Result(lat, lon, info[8], code, speed, course)
    }
}

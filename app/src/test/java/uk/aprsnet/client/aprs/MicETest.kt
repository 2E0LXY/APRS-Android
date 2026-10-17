package uk.aprsnet.client.aprs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

/**
 * Vectors produced by an independent Mic-E encoder and cross-checked against the aprslib
 * decoder (lat/lon agree to < 0.0002 deg). Covers N/S, E/W, and every longitude range
 * (0-9, 10-99, 100-109, 110-179 degrees) - the 0-9 degree range includes the UK.
 */
class MicETest {
    private data class Vec(
        val dest: String, val info: String, val lat: Double, val lon: Double,
        val table: String, val code: String, val course: Int
    )

    private val vectors = listOf(
        Vec("U3TRYQ", "`w<%\u001e<?>/Test", 53.71517, -1.53483, "/", ">", 235),
        Vec("U3TRY1", "`w<%\u001c\u001c\u001c>/Test", 53.71517, 1.53483, "/", ">", 0),
        Vec("S3U2Q3", "`O(T Nvk/Test", -33.86883, 151.20933, "/", "k", 90),
        Vec("S3U21S", "`bD,\u001c\u001c\u001c#\\Test", -33.86883, -70.66933, "\\", "#", 0),
        Vec("T0TR7W", "`fX@\u001d3Wb/Test", 40.71283, -74.00600, "/", "b", 359),
        Vec("U1SPTT", "`v__\"\u001dl>/Test", 51.50733, -0.12783, "/", ">", 180),
        Vec("V4PX8P", "`1TT\u001cN&r\\Test", 64.14667, -21.94267, "\\", "r", 10),
        Vec("S5TPU7", "`CC\u001e&\u001cI>/Test", 35.67617, 139.65033, "/", ">", 45),
        Vec("P0SPP0", "`v:\u001c\u001c&\u001d>/Test", 0.50000, 0.50000, "/", ">", 1),
        Vec("P0S0PP", "`v:\u001c\u001c0\u001e>/Test", -0.50000, -0.50000, "/", ">", 2),
        Vec("U5UWRP", "`y':\u001c\u001c\u001c#DTest", 55.95333, -3.18833, "D", "#", 0),
        Vec("U2PPP0", "`q:\u001c\u001f<b>/Test", 52.00000, 105.50000, "/", ">", 270),
        Vec("T8UQT0", "`x1)#\u001f\u001cj/Test", 48.85667, 2.35217, "/", "j", 300),
        Vec("R8Q1RQ", "`4Wh)bLk/Test", -28.18683, -124.99600, "/", "k", 48),
        Vec("R1R9SY", "`V*.\u001e22>/Test", -21.48983, -158.23633, "/", ">", 222),
        Vec("Q3P55U", "`xNT*D[>\\Test", -13.09250, -92.84267, "\\", ">", 63),
        Vec("W1SU52", "`JIq\u001d1)-/Test", 71.59200, 46.76417, "/", "-", 113),
        Vec("W2S2X1", "`8/v\u001fZh-\\Test", -72.54683, 128.33167, "\\", "-", 276),
        Vec("V1P91Y", "``=m*nxk/Test", -61.15317, -68.56350, "/", "k", 292),
        Vec("R2QS5W", "`IDv!2R>/Test", 22.22617, -45.68167, "/", ">", 254),
        Vec("R8UQ8T", "`5S<*x<O\\Test", 28.86400, -25.92200, "\\", "O", 232),
        Vec("R2P88Q", "`v[l\u001e\u001ezj/Test", -22.14683, -90.06333, "/", "j", 294),
        Vec("S1U7VQ", "`wHv#='O\\Test", -31.96017, -1.74833, "\\", "O", 311),
        Vec("W6TYVX", "`@G{\$bib\\Test", 76.82800, -136.73250, "\\", "b", 77),
        Vec("V9QY3Y", "`8Yx\$eSr/Test", 69.32317, -28.03200, "/", "r", 355),
        Vec("R3U8RY", "`w'F\u001e;Bk\\Test", -23.97150, -1.19033, "\\", "k", 138),
        Vec("P4P866", "`VI\u007f*eL#/Test", -4.14433, 58.76650, "/", "#", 348),
        Vec("U1SP4X", "`i^u\u001cP@r\\Test", 51.50800, -77.11483, "\\", "r", 236),
        Vec("R3P754", "`CFS!OK>\\Test", -23.12567, 39.70917, "\\", ">", 147),
        Vec("U9Q83T", "`v13\u001e\u001cqO\\Test", -59.30567, -90.35383, "\\", "O", 85),
        Vec("P8P780", "`-E}*\u001dF-/Test", -8.13000, 17.69950, "/", "-", 142),
        Vec("S3PQT1", "`fa;\u001flFj\\Test", 33.02350, 174.15517, "\\", "j", 42),
        Vec("U1T83Q", "`{UT(G\u001d>/Test", -51.80517, -95.95933, "/", ">", 301),
        Vec("U0T95Q", "`j\\))Yu-/Test", -50.82517, -78.06883, "/", "-", 189),
        Vec("Q7ST2P", "`\\T2'[L>/Test", 17.57000, -64.93700, "/", ">", 348),
        Vec("T7SY5X", "`B;b\u001eZJ-\\Test", 47.65967, -38.52833, "\\", "-", 246),
        Vec("R1RYQX", "`TFx'0oj/Test", 21.48633, -156.71533, "/", "j", 83),
        Vec("V2R469", "`@[Z*Ni>/Test", -62.41150, 36.06033, "/", ">", 77),
    )

    @Test fun decodesAllVectors() {
        for (v in vectors) {
            val r = MicE.decode(v.dest, v.info)
            assertNotNull("decode failed for ${v.dest}", r)
            assertEquals("lat ${v.dest}", v.lat, r!!.lat, 0.0002)
            assertEquals("lon ${v.dest}", v.lon, r.lon, 0.0002)
            assertEquals("table ${v.dest}", v.table[0], r.symbolTable)
            assertEquals("code ${v.dest}", v.code[0], r.symbolCode)
            assertEquals("course ${v.dest}", v.course, r.courseDeg)
        }
    }

    @Test fun ukStationIsNorthAndWest() {
        val r = MicE.decode(vectors[0].dest, vectors[0].info)!!
        assert(r.lat > 53.0 && r.lat < 54.0) { "lat ${r.lat}" }
        assert(abs(r.lon + 1.5348) < 0.001) { "lon ${r.lon}" }
    }

    @Test fun rejectsGarbage() {
        assertNull(MicE.decode("ABC", "`abcdefgh"))
        assertNull(MicE.decode("U3TRYQ", "`short"))
        assertNull(MicE.decode("U3TRYQ", "!notmice12"))
    }
}

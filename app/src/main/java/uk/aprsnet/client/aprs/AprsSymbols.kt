package uk.aprsnet.client.aprs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import org.json.JSONObject
import uk.aprsnet.client.R
import uk.aprsnet.client.model.StationType

/**
 * Official APRS symbols: the aprs.fi high-resolution set by Heikki Hannikainen OH7LZB
 * (https://github.com/hessu/aprs-symbols) with descriptions from the aprs.fi symbol index
 * (https://github.com/hessu/aprs-symbol-index, CC BY-SA 4.0). See NOTICE.md.
 *
 * Symbol identification (APRS 1.0.1 ch.20 + compressed-position rules). The *table* character is:
 *   '/'            primary table
 *   '\\'           alternate table
 *   'A'-'Z','0'-'9' alternate table + that overlay character
 *   'a'-'j'        alternate table + overlay '0'-'9' (compressed-position encoding)
 *
 * Sheets are 16 columns x 6 rows of 128px cells (res/drawable-nodpi, never rescaled by the
 * resource system). Sheet 0 = primary, 1 = alternate, 2 = overlay characters; the cell for
 * code `c` is idx = c - 33 in every sheet. Cells are decoded on demand with
 * BitmapRegionDecoder (the three full sheets would cost ~19 MB decoded) and composed
 * (symbol + overlay) at the requested pixel size, then cached.
 */
object AprsSymbols {

    private const val CELL = 128
    private const val COLS = 16
    private const val FALLBACK_TABLE = '/'
    private const val FALLBACK_CODE = '/'        // official red dot

    data class Parsed(val sheet: Int, val code: Char, val overlay: Char?, val idx: Int)

    private val sheetRes = intArrayOf(
        R.drawable.aprs_symbols_128_0, R.drawable.aprs_symbols_128_1, R.drawable.aprs_symbols_128_2
    )
    private val decoders = arrayOfNulls<BitmapRegionDecoder>(3)
    private val cells = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val composed = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private var descriptions: Map<String, String>? = null
    @Volatile private var appCtx: Context? = null

    /** Safe to call repeatedly; lets context-free callers (Android Auto renderer) use the engine. */
    fun init(ctx: Context) { if (appCtx == null) appCtx = ctx.applicationContext }

    // ── Identification ────────────────────────────────────────────────────────

    fun parse(table: Char, code: Char): Parsed? {
        if (code.code !in 33..126) return null
        return when (table) {
            '/' -> Parsed(0, code, null, code.code - 33)
            '\\' -> Parsed(1, code, null, code.code - 33)
            in 'A'..'Z', in '0'..'9' -> Parsed(1, code, table, code.code - 33)
            in 'a'..'j' -> Parsed(1, code, '0' + (table - 'a'), code.code - 33)
            else -> null
        }
    }

    fun parse(sym: String): Parsed? = if (sym.length >= 2) parse(sym[0], sym[1]) else null

    /** Canonical 2-char code ("/>" or "D#"), or "" when invalid. */
    fun normalize(table: Char, code: Char): String {
        val p = parse(table, code) ?: return ""
        return "${if (p.sheet == 0) '/' else (p.overlay ?: '\\')}${p.code}"
    }

    fun isValid(table: Char, code: Char) = parse(table, code) != null

    /** Fallback official symbol for stations that never sent a valid one. */
    fun defaultFor(type: StationType): Pair<Char, Char> = when (type) {
        StationType.SHIP -> '/' to 's'
        StationType.GLIDER -> '/' to '\''
        StationType.WEATHER -> '/' to '_'
        StationType.LORA, StationType.MMDVM -> '\\' to '&'
        else -> FALLBACK_TABLE to FALLBACK_CODE
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Synchronized
    private fun cell(ctx: Context, sheet: Int, idx: Int): Bitmap? {
        val key = "$sheet:$idx"
        cells.get(key)?.let { return it }
        val dec = decoders[sheet] ?: runCatching {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(ctx.resources.openRawResource(sheetRes[sheet]), false)
        }.getOrNull()?.also { decoders[sheet] = it } ?: return null
        val x = (idx % COLS) * CELL
        val y = (idx / COLS) * CELL
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = runCatching { dec.decodeRegion(Rect(x, y, x + CELL, y + CELL), opts) }.getOrNull() ?: return null
        cells.put(key, bmp)
        return bmp
    }

    /**
     * The official symbol (plus overlay, when present) composed at [sizePx] x [sizePx].
     * Never null: invalid symbols render the official red dot. The returned bitmap is shared -
     * copy it before drawing on it.
     */
    @Synchronized
    fun bitmap(ctx: Context, table: Char, code: Char, sizePx: Int): Bitmap {
        val p = parse(table, code) ?: parse(FALLBACK_TABLE, FALLBACK_CODE)!!
        val size = sizePx.coerceAtLeast(8)
        val key = "${p.sheet}${p.overlay ?: '-'}${p.code}@$size"
        composed.get(key)?.let { return it }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val dst = Rect(0, 0, size, size)
        cell(ctx, p.sheet, p.idx)?.let { canvas.drawBitmap(it, null, dst, paint) }
        p.overlay?.let { o -> cell(ctx, 2, o.code - 33)?.let { canvas.drawBitmap(it, null, dst, paint) } }
        composed.put(key, out)
        return out
    }

    /** Context-free variant (requires [init] to have been called once). */
    fun bitmap(table: Char, code: Char, sizePx: Int): Bitmap? =
        appCtx?.let { bitmap(it, table, code, sizePx) }

    // ── Descriptions (official aprs.fi symbol index) ──────────────────────────

    @Synchronized
    private fun loadDescriptions(ctx: Context): Map<String, String> {
        descriptions?.let { return it }
        val map = HashMap<String, String>()
        runCatching {
            val text = ctx.assets.open("aprs_symbols.json").bufferedReader().use { it.readText() }
            val j = JSONObject(text)
            j.keys().forEach { k -> map[k] = j.optString(k, "") }
        }
        descriptions = map
        return map
    }

    fun describe(ctx: Context, table: Char, code: Char): String {
        val p = parse(table, code) ?: return "Unknown symbol"
        val d = loadDescriptions(ctx)[(if (p.sheet == 0) "/" else "\\") + p.code].orEmpty()
            .ifEmpty { "Reserved / unassigned" }
        return if (p.overlay != null) "$d (overlay ${p.overlay})" else d
    }

    fun describe(table: Char, code: Char): String =
        appCtx?.let { describe(it, table, code) } ?: "APRS symbol"
}

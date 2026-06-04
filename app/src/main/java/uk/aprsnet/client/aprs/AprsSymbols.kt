package uk.aprsnet.client.aprs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import uk.aprsnet.client.R

/**
 * Real APRS symbol bitmaps, sliced from the standard aprs.fi symbol sheets.
 *
 * Symbol sheets are 384x144 pixels, laid out as 16 columns x 6 rows of
 * 24x24 sprites. The sprite for character `c` lives at index
 *     idx = c.code - 33     (ASCII '!' = 33 is the first symbol)
 *     col = idx % 16
 *     row = idx / 16
 *
 * Tables:
 *   '/'  -> primary (table 0)
 *   '\\' -> secondary (table 1)
 *   table 2 holds the alphanumeric overlay characters used in conjunction
 *   with secondary-table symbols (e.g. an MMDVM hotspot might use
 *   '\' code 'r' with overlay 'M').
 *
 * Source: https://github.com/hessu/aprs-symbols (BSD-style, commercial use
 * permitted with attribution to the source URL).
 *
 * Sprites are cached after first decode so the slicing only runs once per
 * (table, code) pair.
 */
object AprsSymbols {

    private const val SHEET_W = 384
    private const val SHEET_H = 144
    private const val CELLS_PER_ROW = 16
    const val CELL = 24                  // sprite size in source pixels

    private var primary: Bitmap? = null
    private var secondary: Bitmap? = null
    private var overlays: Bitmap? = null

    private val cache = HashMap<String, Bitmap?>()

    /**
     * Pre-decode the three sheets. Safe to call multiple times; subsequent
     * calls are no-ops. Decoding is ~30ms per sheet on a mid-range phone.
     */
    @Synchronized
    fun init(ctx: Context) {
        if (primary != null) return
        val opts = BitmapFactory.Options().apply { inScaled = false }
        primary   = BitmapFactory.decodeResource(ctx.resources, R.drawable.aprs_symbols_24_0, opts)
        secondary = BitmapFactory.decodeResource(ctx.resources, R.drawable.aprs_symbols_24_1, opts)
        overlays  = BitmapFactory.decodeResource(ctx.resources, R.drawable.aprs_symbols_24_2, opts)
    }

    /**
     * Return the 24x24 sprite for the given (table, code) pair, or null
     * if the pair is out of range or the sheet failed to decode.
     */
    fun bitmap(ctx: Context, table: Char, code: Char): Bitmap? {
        init(ctx)
        val key = "$table$code"
        cache[key]?.let { return it }
        val sheet = when (table) {
            '/' -> primary
            '\\' -> secondary
            else -> primary
        } ?: return null
        val idx = code.code - 33
        if (idx < 0 || idx >= CELLS_PER_ROW * (SHEET_H / CELL)) return null
        val col = idx % CELLS_PER_ROW
        val row = idx / CELLS_PER_ROW
        val x = col * CELL
        val y = row * CELL
        if (x + CELL > SHEET_W || y + CELL > SHEET_H) return null
        val sprite = runCatching {
            Bitmap.createBitmap(sheet, x, y, CELL, CELL)
        }.getOrNull()
        cache[key] = sprite
        return sprite
    }

    /**
     * Overlay character sprite (table 2). Used for the digit/letter
     * overlay sometimes drawn on top of a secondary-table symbol.
     * Returns null if no overlay exists for the character.
     */
    fun overlay(ctx: Context, c: Char): Bitmap? {
        init(ctx)
        val sheet = overlays ?: return null
        val key = "ovr:$c"
        cache[key]?.let { return it }
        val idx = c.code - 33
        if (idx < 0 || idx >= CELLS_PER_ROW * (SHEET_H / CELL)) return null
        val col = idx % CELLS_PER_ROW
        val row = idx / CELLS_PER_ROW
        val sprite = runCatching {
            Bitmap.createBitmap(sheet, col * CELL, row * CELL, CELL, CELL)
        }.getOrNull()
        cache[key] = sprite
        return sprite
    }
}
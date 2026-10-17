package uk.aprsnet.client.auto

import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import uk.aprsnet.client.aprs.AprsSymbols

/** Official APRS symbols for Car App Library rows. `sym` is the 2-char table+code. */
object CarSymbols {
    fun icon(sym: String, px: Int = 96): CarIcon? {
        val bmp = if (sym.length >= 2) AprsSymbols.bitmap(sym[0], sym[1], px)
                  else AprsSymbols.bitmap('/', '/', px)
        return bmp?.let { CarIcon.Builder(IconCompat.createWithBitmap(it)).build() }
    }

    fun label(sym: String): String =
        if (sym.length >= 2) AprsSymbols.describe(sym[0], sym[1]) else "APRS station"
}

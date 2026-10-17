package uk.aprsnet.client.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.aprsnet.client.aprs.AprsSymbols

/**
 * Renders the official APRS symbol (aprs.fi high-resolution set) for a (table, code) pair.
 * `table` is the raw table character from the packet: '/', '\\', or an overlay character
 * (A-Z, 0-9, compressed a-j). The overlay glyph is composited on the alternate-table symbol.
 * Rendered at the exact pixel size for the current density; invalid symbols show the official dot.
 */
@Composable
fun AprsSymbolIcon(
    table: Char,
    code: Char,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    @Suppress("UNUSED_PARAMETER") fallbackColour: Color = Color.Unspecified
) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bmp = remember(table, code, px) { AprsSymbols.bitmap(ctx, table, code, px) }
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = AprsSymbols.describe(ctx, table, code),
        filterQuality = FilterQuality.High,
        modifier = modifier.size(size)
    )
}

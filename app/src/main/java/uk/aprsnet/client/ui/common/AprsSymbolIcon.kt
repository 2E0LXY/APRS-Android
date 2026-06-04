package uk.aprsnet.client.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.aprs.AprsSymbols

/**
 * Renders the real APRS symbol sprite for a given (table, code) pair at
 * the requested size. Falls back to a coloured initial-letter avatar if
 * the sprite isn't available (e.g. unknown character or sheet failed to
 * decode).
 *
 * Use sparingly inside lists - each call allocates a slice of the sheet
 * once (cached) and an Image composable thereafter. ~24dp is the natural
 * size since the source sprites are 24x24px.
 */
@Composable
fun AprsSymbolIcon(
    table: Char,
    code: Char,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    fallbackColour: Color = Color(0xFF94A3B8)
) {
    val ctx = LocalContext.current
    val sprite = remember(table, code) { AprsSymbols.bitmap(ctx, table, code) }
    if (sprite != null) {
        Image(
            bitmap = sprite.asImageBitmap(),
            contentDescription = "APRS symbol $table$code",
            modifier = modifier.size(size)
        )
    } else {
        // Plain coloured circle with the symbol code as a single character
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(fallbackColour),
            contentAlignment = Alignment.Center
        ) {
            Text(
                code.toString(),
                color = Color.White,
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
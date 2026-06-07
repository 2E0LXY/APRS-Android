package uk.aprsnet.client.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Atmospheric background painter for the messages section
 * (ConversationListScreen + ThreadScreen).
 *
 * Why programmatic (Compose Brush) instead of embedded PNG assets:
 *  - Zero APK weight. A single 1080x2400 PNG of a textured background
 *    averages 400-600 KB; embedding 5+ (one per theme) means 2-3 MB of
 *    asset bloat for what is essentially gradient + noise.
 *  - Theme-aware. The radial gradient centre, the dominant colour, and
 *    the vignette darkness all derive from the user's chosen AppTheme,
 *    so the messages background harmonises with the rest of the UI
 *    automatically. Switching theme = instant repaint, no asset swap.
 *  - Resolution-independent. Looks crisp on any screen, no scaling.
 *  - No licensing question. Generated pixels, not stock photography.
 *
 * The visual recipe (dark teal aesthetic):
 *  1. Vertical linear gradient from a top mid-tone to a deeper bottom
 *     tone (the canvas).
 *  2. A large off-centre radial gradient hotspot (the "glow").
 *  3. A subtle pseudo-noise speckle of slightly lighter/darker dots
 *     (the "grain"). Seeded by canvas size so it stays stable across
 *     recompositions and does not flicker.
 *  4. Vignette: corners darkened with a radial gradient.
 *
 * Apply at the root of a screen by wrapping content:
 *
 *     MessageBackground(themeIndex = vm.settings.themeId) {
 *         // your screen content here
 *     }
 */
@Composable
fun MessageBackground(
    themeIndex: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = remember(themeIndex) { paletteFor(themeIndex) }

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: base gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(palette.top, palette.bottom)
                    )
                )
        )
        // Layer 2: off-centre radial glow + grain + vignette in one Canvas pass
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGlow(palette)
            drawGrain(palette)
            drawVignette(palette)
        }
        // Foreground content
        content()
    }
}

private data class BgPalette(
    val top: Color,
    val bottom: Color,
    val glow: Color,
    val grainBright: Color,
    val grainDark: Color,
    val vignette: Color
)

/**
 * Theme-keyed palettes. Index aligns with APP_THEMES order in
 * ui/theme/AppearancePalette.kt (Navy/Sunset/Forest/Aurora/Mono).
 * Default = dark teal (matches the user-supplied reference images
 * for the messages section).
 */
private fun paletteFor(themeIndex: Int): BgPalette {
    return when (themeIndex) {
        // Sunset - orange/amber gradient bands
        1 -> BgPalette(
            top          = Color(0xFF2A0E04),
            bottom       = Color(0xFF120602),
            glow         = Color(0x55FF7A1A),
            grainBright  = Color(0x14FFA040),
            grainDark    = Color(0x14000000),
            vignette     = Color(0x88000000)
        )
        // Forest - dark green radial
        2 -> BgPalette(
            top          = Color(0xFF062013),
            bottom       = Color(0xFF010A06),
            glow         = Color(0x553BB05E),
            grainBright  = Color(0x144CC076),
            grainDark    = Color(0x14000000),
            vignette     = Color(0x88000000)
        )
        // Aurora - cool blue/teal
        3 -> BgPalette(
            top          = Color(0xFF062534),
            bottom       = Color(0xFF010E18),
            glow         = Color(0x554EBFD9),
            grainBright  = Color(0x1466D7E8),
            grainDark    = Color(0x14000000),
            vignette     = Color(0x88000000)
        )
        // Mono - desaturated graphite
        4 -> BgPalette(
            top          = Color(0xFF18181B),
            bottom       = Color(0xFF050507),
            glow         = Color(0x55454555),
            grainBright  = Color(0x14808090),
            grainDark    = Color(0x14000000),
            vignette     = Color(0x88000000)
        )
        // Default + Navy (index 0): DARK TEAL - matches the supplied
        // reference images (shutterstock dark-teal-science aesthetic).
        else -> BgPalette(
            top          = Color(0xFF0A3B43),
            bottom       = Color(0xFF03191E),
            glow         = Color(0x553AB0B8),
            grainBright  = Color(0x144EC0CC),
            grainDark    = Color(0x14000000),
            vignette     = Color(0x88000000)
        )
    }
}

private fun DrawScope.drawGlow(p: BgPalette) {
    // Off-centre radial hotspot, roughly upper-right.
    val cx = size.width * 0.62f
    val cy = size.height * 0.28f
    val r  = sqrt(size.width * size.width + size.height * size.height) * 0.55f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(p.glow, Color.Transparent),
            center = Offset(cx, cy),
            radius = r
        ),
        topLeft = Offset.Zero,
        size = size
    )
}

private fun DrawScope.drawGrain(p: BgPalette) {
    // Stable per-frame: seed comes from the canvas dimensions, so the
    // speckle does not jitter on recomposition. ~3500 specks scales
    // well visually and is cheap (<2ms on any modern Pixel).
    val rng = Random(seed = (size.width.toInt() * 31 + size.height.toInt()))
    val count = 3500
    val w = size.width
    val h = size.height
    repeat(count) {
        val x = rng.nextFloat() * w
        val y = rng.nextFloat() * h
        val bright = rng.nextFloat() < 0.5f
        val c = if (bright) p.grainBright else p.grainDark
        val radius = 0.6f + rng.nextFloat() * 0.9f
        drawCircle(color = c, radius = radius, center = Offset(x, y))
    }
}

private fun DrawScope.drawVignette(p: BgPalette) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val r  = sqrt(size.width * size.width + size.height * size.height) * 0.65f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, p.vignette),
            center = Offset(cx, cy),
            radius = r
        ),
        topLeft = Offset.Zero,
        size = size
    )
}
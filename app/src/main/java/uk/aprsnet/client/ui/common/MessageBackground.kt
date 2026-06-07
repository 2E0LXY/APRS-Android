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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Selectable painterly backgrounds for the Messages section.
 *
 * Seven preset styles, all generated programmatically (no PNG assets):
 *   0  Dark Teal       - gradient + radial glow + grain + vignette
 *   1  Bright Teal     - lighter canvas variant
 *   2  Green Grid      - dark green with diagonal grid overlay
 *   3  Green Spotlight - deep green with strong central radial spotlight
 *   4  Red Flow        - black with curved red abstract shapes
 *   5  Orange Stripes  - dark base with diagonal orange bands
 *   6  Sunset Gradient - warm orange/amber sweep
 *
 * Index 0 is the default. The user picks via Settings > Appearance.
 *
 * Why programmatic: zero APK weight (PNG assets for 7 themed backgrounds
 * would add 3-5 MB), resolution-independent, no licensing question.
 */

/** Human-readable names for the Settings picker. Index matches the id. */
val MESSAGE_BG_NAMES = listOf(
    "Dark Teal",
    "Bright Teal",
    "Green Grid",
    "Green Spotlight",
    "Red Flow",
    "Orange Stripes",
    "Sunset Gradient"
)

@Composable
fun MessageBackground(
    backgroundId: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val style = remember(backgroundId) { backgroundId.coerceIn(0, 6) }
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (style) {
                0 -> drawDarkTeal(this)
                1 -> drawBrightTeal(this)
                2 -> drawGreenGrid(this)
                3 -> drawGreenSpotlight(this)
                4 -> drawRedFlow(this)
                5 -> drawOrangeStripes(this)
                6 -> drawSunsetGradient(this)
            }
        }
        content()
    }
}

// ---------------------------------------------------------------------------
// Helpers shared across styles
// ---------------------------------------------------------------------------

private fun DrawScope.fillVertical(top: Color, bottom: Color) {
    drawRect(
        brush = Brush.verticalGradient(listOf(top, bottom)),
        topLeft = Offset.Zero,
        size = size
    )
}

private fun DrawScope.radialGlow(centerX: Float, centerY: Float, radius: Float, color: Color) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(size.width * centerX, size.height * centerY),
            radius = radius
        ),
        topLeft = Offset.Zero,
        size = size
    )
}

private fun DrawScope.vignette(strength: Color) {
    val r = sqrt(size.width * size.width + size.height * size.height) * 0.65f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, strength),
            center = Offset(size.width * 0.5f, size.height * 0.5f),
            radius = r
        ),
        topLeft = Offset.Zero,
        size = size
    )
}

private fun DrawScope.grain(count: Int, bright: Color, dark: Color) {
    val rng = Random(seed = (size.width.toInt() * 31 + size.height.toInt()))
    repeat(count) {
        val x = rng.nextFloat() * size.width
        val y = rng.nextFloat() * size.height
        val c = if (rng.nextFloat() < 0.5f) bright else dark
        drawCircle(color = c, radius = 0.6f + rng.nextFloat() * 0.9f, center = Offset(x, y))
    }
}

// ---------------------------------------------------------------------------
// Style 0 - Dark Teal (reference image 1)
// ---------------------------------------------------------------------------

private fun drawDarkTeal(scope: DrawScope) = with(scope) {
    val diag = sqrt(size.width * size.width + size.height * size.height)
    fillVertical(Color(0xFF0A3B43), Color(0xFF03191E))
    radialGlow(0.62f, 0.28f, diag * 0.55f, Color(0x553AB0B8))
    grain(3500, Color(0x144EC0CC), Color(0x14000000))
    vignette(Color(0x88000000))
}

// ---------------------------------------------------------------------------
// Style 1 - Bright Teal (reference image 2)
// ---------------------------------------------------------------------------

private fun drawBrightTeal(scope: DrawScope) = with(scope) {
    val diag = sqrt(size.width * size.width + size.height * size.height)
    fillVertical(Color(0xFF14555E), Color(0xFF062931))
    radialGlow(0.35f, 0.30f, diag * 0.55f, Color(0x6655C9D4))
    grain(2800, Color(0x1C66D7E2), Color(0x12000000))
    vignette(Color(0x77000000))
}

// ---------------------------------------------------------------------------
// Style 2 - Green Grid (reference image 3)
// ---------------------------------------------------------------------------

private fun drawGreenGrid(scope: DrawScope) = with(scope) {
    val diag = sqrt(size.width * size.width + size.height * size.height)
    fillVertical(Color(0xFF0A4A22), Color(0xFF02160A))
    radialGlow(0.5f, 0.5f, diag * 0.5f, Color(0x4422A055))
    // Diagonal grid - two crossing line sets at +/- 45 degrees
    val spacing = 18f
    val lineColor = Color(0x33000000)
    val stroke = Stroke(width = 0.8f)
    rotate(degrees = 45f, pivot = Offset(size.width * 0.5f, size.height * 0.5f)) {
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = lineColor,
                start = Offset(x, -size.height),
                end   = Offset(x, size.height * 2f),
                strokeWidth = 0.8f
            )
            x += spacing
        }
    }
    rotate(degrees = -45f, pivot = Offset(size.width * 0.5f, size.height * 0.5f)) {
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = lineColor,
                start = Offset(x, -size.height),
                end   = Offset(x, size.height * 2f),
                strokeWidth = 0.8f
            )
            x += spacing
        }
    }
    vignette(Color(0x99000000))
}

// ---------------------------------------------------------------------------
// Style 3 - Green Spotlight (reference image 4)
// ---------------------------------------------------------------------------

private fun drawGreenSpotlight(scope: DrawScope) = with(scope) {
    val diag = sqrt(size.width * size.width + size.height * size.height)
    drawRect(color = Color.Black, topLeft = Offset.Zero, size = size)
    radialGlow(0.45f, 0.35f, diag * 0.55f, Color(0xCC1E7A2A))
    grain(2200, Color(0x1A2DCC4D), Color(0x14000000))
    vignette(Color(0xCC000000))
}

// ---------------------------------------------------------------------------
// Style 4 - Red Flow (reference image 5)
// ---------------------------------------------------------------------------

private fun drawRedFlow(scope: DrawScope) = with(scope) {
    drawRect(color = Color(0xFF0A0203), topLeft = Offset.Zero, size = size)
    val w = size.width
    val h = size.height
    // Sweeping curved ribbon - lower-left to upper-right
    val ribbon = Path().apply {
        moveTo(0f, h * 0.75f)
        cubicTo(w * 0.30f, h * 0.30f, w * 0.55f, h * 1.10f, w, h * 0.40f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(
        path = ribbon,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFB81020), Color(0xFF2A0408)),
            start = Offset(0f, h),
            end   = Offset(w, 0f)
        )
    )
    // Secondary smaller curve highlight
    val highlight = Path().apply {
        moveTo(w * 0.10f, h * 0.40f)
        cubicTo(w * 0.40f, h * 0.05f, w * 0.70f, h * 0.55f, w * 0.90f, h * 0.20f)
        cubicTo(w * 0.70f, h * 0.30f, w * 0.40f, h * 0.20f, w * 0.10f, h * 0.40f)
        close()
    }
    drawPath(
        path = highlight,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xCCFF2030), Color(0x66800010)),
            start = Offset(0f, 0f),
            end   = Offset(w, h)
        )
    )
    vignette(Color(0xAA000000))
}

// ---------------------------------------------------------------------------
// Style 5 - Orange Stripes (reference image 6)
// ---------------------------------------------------------------------------

private fun drawOrangeStripes(scope: DrawScope) = with(scope) {
    drawRect(color = Color(0xFF0A0503), topLeft = Offset.Zero, size = size)
    val w = size.width
    val h = size.height
    rotate(degrees = -28f, pivot = Offset(w * 0.5f, h * 0.5f)) {
        val stripeWidth = h * 0.16f
        val gap = h * 0.10f
        var y = -h
        var i = 0
        while (y < h * 2f) {
            val brightness = if (i % 2 == 0) 0xFFFF6A1A.toInt() else 0xFFCC4810.toInt()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black,
                        Color(brightness),
                        Color.Black
                    ),
                    startY = y,
                    endY = y + stripeWidth
                ),
                topLeft = Offset(-w, y),
                size = Size(w * 3f, stripeWidth)
            )
            y += stripeWidth + gap
            i++
        }
    }
    grain(3000, Color(0x1AFFAA40), Color(0x15000000))
    vignette(Color(0x99000000))
}

// ---------------------------------------------------------------------------
// Style 6 - Sunset Gradient (reference image 7)
// ---------------------------------------------------------------------------

private fun drawSunsetGradient(scope: DrawScope) = with(scope) {
    val w = size.width
    val h = size.height
    // Diagonal linear gradient: amber upper-left to deep red lower-right
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFB020),
                Color(0xFFFF6F10),
                Color(0xFFA02208),
                Color(0xFF400804)
            ),
            start = Offset(0f, 0f),
            end   = Offset(w, h)
        ),
        topLeft = Offset.Zero,
        size = size
    )
    // Soft horizontal sweep highlight
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0x55FFFFFF), Color.Transparent, Color(0x33000000)),
            startY = 0f,
            endY = h
        ),
        topLeft = Offset.Zero,
        size = size
    )
    grain(2000, Color(0x1AFFFFFF), Color(0x14000000))
}
package uk.aprsnet.client.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Theme + bubble-colour palettes selectable from Settings -> Appearance.
 *
 * Themes determine the *page background gradient* and don't replace the
 * Material3 palette wholesale - text colours, panel colours and accents
 * stay constant for legibility. This keeps theming purely decorative and
 * cannot accidentally make text unreadable.
 *
 * Bubble colours are applied to outgoing message bubbles in ThreadScreen.
 * ACKed bubbles always use lime->green so the success cue stays consistent
 * regardless of user choice.
 */
data class AppTheme(
    val id: Int,
    val label: String,
    val gradientTop: Color,
    val gradientBottom: Color
)

val APP_THEMES = listOf(
    AppTheme(0, "Navy",    Color(0xFF0A0F1C), Color(0xFF131A2C)),  // default
    AppTheme(1, "Sunset",  Color(0xFF2C0F1C), Color(0xFF1C0A1A)),  // rose tinted
    AppTheme(2, "Forest",  Color(0xFF0A1F1C), Color(0xFF0F1A14)),  // emerald tinted
    AppTheme(3, "Aurora",  Color(0xFF150A2C), Color(0xFF0A1A2C)),  // purple->cyan
    AppTheme(4, "Mono",    Color(0xFF0A0A0A), Color(0xFF141414))   // pure dark
)

data class BubblePalette(
    val id: Int,
    val label: String,
    val top: Color,
    val bottom: Color
)

val BUBBLE_PALETTES = listOf(
    BubblePalette(0, "Cyan",   Color(0xFF06B6D4), Color(0xFF0E7490)),  // default
    BubblePalette(1, "Amber",  Color(0xFFF59E0B), Color(0xFFB45309)),
    BubblePalette(2, "Lime",   Color(0xFF84CC16), Color(0xFF4D7C0F)),
    BubblePalette(3, "Purple", Color(0xFFA855F7), Color(0xFF6B21A8)),
    BubblePalette(4, "Rose",   Color(0xFFEC4899), Color(0xFF9F1239)),
    BubblePalette(5, "Blue",   Color(0xFF3B82F6), Color(0xFF1E3A8A))
)
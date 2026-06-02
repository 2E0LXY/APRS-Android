package uk.aprsnet.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Accent,
    secondary = AccentBlue,
    background = BgDeep,
    surface = BgPanel,
    onPrimary = TextBase,
    onBackground = TextBase,
    onSurface = TextBase
)

private val LightColors = lightColorScheme(
    primary = AccentBlue,
    secondary = Accent
)

@Composable
fun AprsNetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AprsTypography,
        content = content
    )
}
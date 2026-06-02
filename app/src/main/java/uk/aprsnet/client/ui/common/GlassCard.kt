package uk.aprsnet.client.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.BgPanel
import uk.aprsnet.client.ui.theme.BgPanelHi
import uk.aprsnet.client.ui.theme.BorderCol

/**
 * Glass-style card used across Settings, Status, dialogs.
 * Rounded 18.dp, panel background with a faint vertical highlight so the top
 * edge picks up a soft glassy sheen against the deeper page background.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(BgPanelHi, BgPanel)
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .border(
                BorderStroke(1.dp, BorderCol),
                RoundedCornerShape(18.dp)
            )
            .padding(contentPadding)
    ) {
        if (title != null) {
            Text(
                title.uppercase(),
                color = Accent,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.size(10.dp))
        }
        content()
    }
}
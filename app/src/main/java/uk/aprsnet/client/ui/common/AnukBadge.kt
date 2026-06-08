package uk.aprsnet.client.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small inline badge shown next to callsigns of registered APRS Net members.
 * "ANUK" = APRS Net UK.  Teal background to match the brand colour.
 */
@Composable
fun AnukBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF0D9488), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = "ANUK",
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

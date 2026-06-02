package uk.aprsnet.client.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.ui.theme.BorderCol
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim

/**
 * Circular gauge with a gradient stroke. Used on Status to show packet rate,
 * stations heard, uptime etc.
 *  - track is a dim ring at full circle
 *  - progress is drawn over the top, gradient-stroked, sweep from top
 *  - centre shows two text lines (value + label)
 */
@Composable
fun RingProgress(
    fraction: Float,                            // 0f..1f
    value: String,
    label: String,
    sizeDp: Int = 110,
    strokeWidthDp: Int = 10,
    gradient: List<Color>,
    modifier: Modifier = Modifier
) {
    val pct = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier.size(sizeDp.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidthDp.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // track
            drawArc(
                color = BorderCol,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // progress
            if (pct > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(gradient),
                    startAngle = -90f,
                    sweepAngle = 360f * pct,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = TextBase, fontSize = 20.sp,
                fontWeight = FontWeight.Bold)
            Text(label, color = TextDim, fontSize = 10.sp,
                letterSpacing = 0.5.sp)
        }
    }
}
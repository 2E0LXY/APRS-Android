package uk.aprsnet.client.ui.iss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import uk.aprsnet.client.net.AprsApi
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.BgPanel
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim

/** ISS live position, refreshed every 15 seconds. */
@Composable
fun IssScreen(modifier: Modifier = Modifier) {
    var iss by remember { mutableStateOf<AprsApi.IssPosition?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) {
            val pos = AprsApi.issPosition()
            if (pos != null) iss = pos
            loading = false
            delay(15_000)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Card("International Space Station") {
            val pos = iss
            when {
                loading && pos == null ->
                    Text("Locating the ISS...", color = TextDim, fontSize = 13.sp)
                pos == null ->
                    Text("ISS position unavailable", color = TextDim, fontSize = 13.sp)
                else -> {
                    StatRow("Latitude", "%.4f".format(pos.lat))
                    StatRow("Longitude", "%.4f".format(pos.lon))
                    StatRow("Altitude", "%.0f km".format(pos.altitudeKm))
                    StatRow("Velocity", "%.0f km/h".format(pos.velocityKmh))
                }
            }
        }
        Card("About") {
            Text(
                "The ISS orbits the Earth roughly every 90 minutes at about " +
                    "408 km altitude. Position refreshes every 15 seconds.",
                color = TextDim, fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgPanel)
            .padding(14.dp)
    ) {
        Text(title.uppercase(), color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.size(8.dp))
        content()
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextBase, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
package uk.aprsnet.client.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.BgPanel
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim

/**
 * About / info screen - app version, the project, links to the web
 * dashboard and the other clients.
 */
@Composable
fun InfoScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Card("APRS Net") {
            Text("Native Android client", color = TextBase, fontSize = 14.sp)
            Text("Version 2.0.0", color = TextDim, fontSize = 12.sp)
        }
        Card("About") {
            Text(
                "A fully native APRS client for the Advanced APRS Go " +
                    "Server. Live map, messaging, smart-beaconing GPS, " +
                    "contacts and station tracking - all over a single " +
                    "WebSocket to www.aprsnet.uk.",
                color = TextDim, fontSize = 13.sp
            )
        }
        Card("Links") {
            Text("Web dashboard: www.aprsnet.uk", color = Accent, fontSize = 13.sp)
            Spacer(Modifier.size(4.dp))
            Text(
                "Source: github.com/2E0LXY/APRS-Android",
                color = Accent, fontSize = 13.sp
            )
        }
        Card("Licence") {
            Text(
                "GNU General Public Licence v3\n(c) 2026 Daren Loxley 2E0LXY",
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
        Text(
            title.uppercase(),
            color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp
        )
        Spacer(Modifier.size(8.dp))
        content()
    }
}
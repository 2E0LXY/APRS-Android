package uk.aprsnet.client.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.net.AprsWebSocket
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.BgPanel
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim

/** Server status + connection + station counts. */
@Composable
fun StatusScreen(vm: AprsViewModel, modifier: Modifier = Modifier) {
    val status by vm.status.collectAsState()
    val conn by vm.connState.collectAsState()
    val stations by vm.stations.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Card("Connection") {
            StatRow("WebSocket", when (conn) {
                AprsWebSocket.ConnState.AUTHED -> "Connected (authenticated)"
                AprsWebSocket.ConnState.CONNECTED -> "Connected"
                AprsWebSocket.ConnState.CONNECTING -> "Connecting..."
                AprsWebSocket.ConnState.DISCONNECTED -> "Disconnected"
            })
            StatRow("Stations on map", stations.size.toString())
        }

        Card("Server") {
            val s = status
            if (s == null) {
                Text("Loading server status...", color = TextDim, fontSize = 13.sp)
            } else {
                StatRow("Uptime", s.uptime)
                StatRow("Packets received", s.packetsRx.toString())
                StatRow("Upstream", if (s.upstreamConnected) "Connected" else "Down")
                StatRow("Server stations", s.stations.toString())
            }
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
            color = Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
        Column(Modifier.padding(top = 8.dp)) { content() }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(label, color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextBase, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
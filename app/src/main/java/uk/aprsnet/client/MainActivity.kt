package uk.aprsnet.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.aprsnet.client.net.AprsWebSocket
import uk.aprsnet.client.ui.map.MapScreen
import uk.aprsnet.client.ui.theme.AprsNetTheme
import uk.aprsnet.client.ui.theme.BgHeader
import uk.aprsnet.client.ui.theme.Err
import uk.aprsnet.client.ui.theme.Ok

/**
 * APRS Net - native Android app, single activity.
 * Stage 1: connects the WebSocket and shows the live map of stations.
 * Later stages add messaging, beaconing, contacts, settings, admin.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AprsNetTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val vm: AprsViewModel = viewModel()
    val conn by vm.connState.collectAsState()
    val stations by vm.stations.collectAsState()

    // Stage 1: connect anonymously (no callsign yet - that arrives with the
    // Settings screen in a later stage). The live feed works without auth.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.start("", "")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StatusBar(conn, stations.size)
        Box(modifier = Modifier.fillMaxSize()) {
            MapScreen(vm)
        }
    }
}

@Composable
private fun StatusBar(conn: AprsWebSocket.ConnState, stationCount: Int) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgHeader)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("APRS Net", color = Color(0xFF60A5FA))
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Text("$stationCount stn", color = Color(0xFF94A3B8))
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        val dot = when (conn) {
            AprsWebSocket.ConnState.AUTHED,
            AprsWebSocket.ConnState.CONNECTED -> Ok
            else -> Err
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dot)
        )
    }
}
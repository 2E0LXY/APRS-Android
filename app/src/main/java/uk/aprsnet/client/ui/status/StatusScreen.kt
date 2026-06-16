package uk.aprsnet.client.ui.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.net.AprsWebSocket
import uk.aprsnet.client.net.BleKissManager
import uk.aprsnet.client.ui.common.GlassCard
import uk.aprsnet.client.ui.common.RingProgress
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.AccentAmber
import uk.aprsnet.client.ui.theme.AccentLime
import uk.aprsnet.client.ui.theme.AccentPurple
import uk.aprsnet.client.ui.theme.Err
import uk.aprsnet.client.ui.theme.Ok
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim

/**
 * Server status + connection + station counts.
 * v2.3: three gradient-stroked ring gauges across the top.
 * v2.7: added BLE Radio card for RT-950 Pro connection status + control.
 */
@Composable
fun StatusScreen(vm: AprsViewModel, modifier: Modifier = Modifier) {
    val status      by vm.status.collectAsState()
    val conn        by vm.connState.collectAsState()
    val stations    by vm.stations.collectAsState()
    val lastBeaconAt by vm.beacon.lastBeaconAt.collectAsState()
    val myFix       by vm.myPosition.collectAsState()
    val bleState    by vm.ble.state.collectAsState()
    val blePktCnt   by vm.ble.pktCount.collectAsState()
    val bleDevName  by vm.ble.deviceName.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        // --- ring gauges -----------------------------------------------
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val s = status
                RingProgress(
                    fraction = (stations.size / 1000f).coerceAtMost(1f),
                    value = stations.size.toString(),
                    label = "ON MAP",
                    gradient = listOf(Accent, AccentLime, Accent)
                )
                RingProgress(
                    fraction = if (s != null && s.upstreamConnected) 1f else 0f,
                    value = if (s != null && s.upstreamConnected) "UP" else "DOWN",
                    label = "UPSTREAM",
                    gradient = listOf(AccentLime, Accent, AccentLime)
                )
                RingProgress(
                    fraction = when (conn) {
                        AprsWebSocket.ConnState.AUTHED -> 1f
                        AprsWebSocket.ConnState.CONNECTED -> 0.7f
                        AprsWebSocket.ConnState.CONNECTING -> 0.3f
                        AprsWebSocket.ConnState.DISCONNECTED -> 0f
                    },
                    value = when (conn) {
                        AprsWebSocket.ConnState.AUTHED -> "AUTH"
                        AprsWebSocket.ConnState.CONNECTED -> "CONN"
                        AprsWebSocket.ConnState.CONNECTING -> "..."
                        AprsWebSocket.ConnState.DISCONNECTED -> "OFF"
                    },
                    label = "WS",
                    gradient = listOf(AccentPurple, Accent, AccentPurple)
                )
            }
        }

        // --- connection ------------------------------------------------
        GlassCard(title = "Connection") {
            StatRow("WebSocket", when (conn) {
                AprsWebSocket.ConnState.AUTHED -> "Connected (authenticated)"
                AprsWebSocket.ConnState.CONNECTED -> "Connected"
                AprsWebSocket.ConnState.CONNECTING -> "Connecting..."
                AprsWebSocket.ConnState.DISCONNECTED -> "Disconnected"
            }, valueColour = when (conn) {
                AprsWebSocket.ConnState.AUTHED -> Ok
                AprsWebSocket.ConnState.DISCONNECTED -> Err
                else -> TextBase
            })
            StatRow("Stations on map", stations.size.toString())
        }

        // --- beacon ----------------------------------------------------
        GlassCard(title = "Position beacon") {
            val mode = vm.settings.positionMode
            StatRow("Mode", mode.replaceFirstChar { it.uppercase() },
                valueColour = if (mode == "smart") Ok else AccentAmber)
            StatRow("Have GPS fix?", if (myFix == null) "No" else "Yes",
                valueColour = if (myFix == null) Err else Ok)
            StatRow("Last beacon", beaconAgeText(lastBeaconAt))
            if (mode == "off") {
                Text(
                    "Position mode is OFF. Go to Settings -> Position / Beaconing " +
                        "and set Smart to start transmitting to APRS-IS.",
                    color = AccentAmber, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            } else if (lastBeaconAt == 0L && myFix != null) {
                Text(
                    "No beacon sent yet. Long-press the location FAB on the map to " +
                        "force one, or move around to trigger the smart-beacon.",
                    color = AccentAmber, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // --- server ----------------------------------------------------
        GlassCard(title = "Server") {
            val s = status
            if (s == null) {
                Text("Loading server status...", color = TextDim, fontSize = 13.sp)
            } else {
                StatRow("Uptime", s.uptime)
                StatRow("Packets received", s.packetsRx.toString())
                StatRow("Upstream", if (s.upstreamConnected) "Connected" else "Down",
                    valueColour = if (s.upstreamConnected) Ok else Err)
                StatRow("Server stations", s.stations.toString())
            }
        }

        // --- BLE radio -------------------------------------------------
        GlassCard(title = "BLE Radio") {
            StatRow(
                label = "Status",
                value = when (bleState) {
                    BleKissManager.BleState.CONNECTED    -> bleDevName ?: "Connected"
                    BleKissManager.BleState.CONNECTING   -> "Connecting…"
                    BleKissManager.BleState.SCANNING     -> "Scanning…"
                    BleKissManager.BleState.DISCONNECTED -> "Not connected"
                },
                valueColour = when (bleState) {
                    BleKissManager.BleState.CONNECTED    -> Ok
                    BleKissManager.BleState.DISCONNECTED -> TextDim
                    else                                 -> AccentAmber
                }
            )
            if (bleState == BleKissManager.BleState.CONNECTED) {
                StatRow("Packets", blePktCnt.toString(), valueColour = Accent)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (bleState == BleKissManager.BleState.DISCONNECTED) vm.startBle()
                    else vm.stopBle()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (bleState == BleKissManager.BleState.DISCONNECTED)
                        Accent else Err
                )
            ) {
                Text(
                    if (bleState == BleKissManager.BleState.DISCONNECTED)
                        "Connect BLE Radio" else "Disconnect"
                )
            }
            if (!vm.ble.hasPermissions()) {
                Text(
                    "Bluetooth permissions required — grant in device settings.",
                    color = AccentAmber, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColour: androidx.compose.ui.graphics.Color = TextBase
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColour, fontSize = 13.sp,
            fontWeight = FontWeight.Bold)
    }
}

private fun beaconAgeText(ts: Long): String {
    if (ts <= 0L) return "Never"
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "${diff / 1000}s ago"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        else -> "${diff / 3_600_000}h ago"
    }
}

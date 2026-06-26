package uk.aprsnet.client.wear.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import uk.aprsnet.client.wear.data.WearDataBridge
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(nav: NavController) {
    val status   by WearDataBridge.status.collectAsState()
    val unread      = WearDataBridge.unreadCount

    ScalingLazyColumn(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding      = PaddingValues(vertical = 24.dp)
    ) {
        // ── Status header ─────────────────────────────────────────────────
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = status.myCallsign.ifBlank { "APRS Net" },
                    fontSize   = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colors.primary
                )
                Text(
                    text     = if (status.connected) "● Connected" else "○ Offline",
                    fontSize = 11.sp,
                    color    = if (status.connected) Color(0xFF4CAF50) else Color.Gray
                )
            }
        }

        // ── Station count ─────────────────────────────────────────────────
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(0.85f),
                onClick  = { nav.navigate("stations") },
                label    = { Text("${status.stationCount} Stations") },
                colors   = ChipDefaults.primaryChipColors()
            )
        }

        // ── Messages ──────────────────────────────────────────────────────
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(0.85f),
                onClick  = { nav.navigate("messages") },
                label    = {
                    Text(if (unread > 0) "Messages ($unread unread)" else "Messages")
                },
                colors   = if (unread > 0) ChipDefaults.primaryChipColors()
                           else ChipDefaults.secondaryChipColors()
            )
        }

        // ── Beacon ────────────────────────────────────────────────────────
        item {
            val lastBeacon = if (status.lastBeaconTs > 0L) {
                val ago = (System.currentTimeMillis() - status.lastBeaconTs * 1000) / 60_000
                when {
                    ago < 1    -> "Just beaconed"
                    ago < 60   -> "${ago}m ago"
                    else       -> "${ago / 60}h ago"
                }
            } else "No beacon yet"

            Chip(
                modifier = Modifier.fillMaxWidth(0.85f),
                onClick  = { nav.navigate("beacon") },
                label    = { Text("Beacon") },
                secondaryLabel = { Text(lastBeacon, fontSize = 10.sp) },
                colors   = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

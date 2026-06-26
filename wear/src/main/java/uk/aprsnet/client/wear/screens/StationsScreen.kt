package uk.aprsnet.client.wear.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import uk.aprsnet.client.wear.data.WearDataBridge

@Composable
fun StationsScreen(nav: NavController) {
    val stations by WearDataBridge.stations.collectAsState()

    ScalingLazyColumn(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding      = PaddingValues(vertical = 24.dp)
    ) {
        item {
            Text(
                "Nearby Stations",
                style = MaterialTheme.typography.title3
            )
        }

        if (stations.isEmpty()) {
            item {
                Text("No stations", color = androidx.compose.ui.graphics.Color.Gray, fontSize = 12.sp)
            }
        }

        items(stations.take(20)) { stn ->
            val ageMin = ((System.currentTimeMillis() - stn.lastHeardMs) / 60_000).toInt()
            val ageStr = when {
                ageMin < 60   -> "${ageMin}m"
                ageMin < 1440 -> "${ageMin / 60}h"
                else          -> "${ageMin / 1440}d"
            }
            val distStr = if (stn.distKm < 1.0) "${(stn.distKm * 1000).toInt()}m"
                          else "${"%.1f".format(stn.distKm)}km"

            TitleCard(
                modifier    = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 2.dp),
                onClick     = {},
                title       = { Text(stn.callsign, fontSize = 14.sp) },
                time        = { Text("$distStr · $ageStr", fontSize = 10.sp) }
            ) {
                Text(
                    stn.comment.ifBlank { "—" },
                    fontSize  = 11.sp,
                    maxLines  = 1,
                    overflow  = TextOverflow.Ellipsis
                )
            }
        }
    }
}

package uk.aprsnet.client.ui.stations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.model.Station
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.TextDim
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Searchable list of all heard stations, with distance/bearing from the user. */
@Composable
fun StationsScreen(
    vm: AprsViewModel,
    onOpenStation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val stations by vm.stations.collectAsState()
    val myPos by vm.myPosition.collectAsState()
    var query by remember { mutableStateOf("") }

    val list = remember(stations, query, myPos) {
        stations.values
            .filter { query.isBlank() || it.callsign.contains(query.uppercase()) }
            .sortedBy { st ->
                val p = myPos
                if (p != null) distanceKm(p.lat, p.lon, st.lat, st.lon) else 0.0
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            placeholder = { Text("Filter callsign...") },
            singleLine = true
        )
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No stations heard yet", color = TextDim)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list) { st ->
                    StationRow(st, myPos?.let {
                        distanceKm(it.lat, it.lon, st.lat, st.lon)
                    }) { onOpenStation(st.callsign) }
                }
            }
        }
    }
}

@Composable
private fun StationRow(st: Station, distKm: Double?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(st.callsign, color = Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                ageText(st.lastHeard) +
                    "  -  " + "%.3f, %.3f".format(st.lat, st.lon),
                color = TextDim, fontSize = 12.sp
            )
        }
        if (distKm != null) {
            Spacer(Modifier.size(8.dp))
            Text("${distKm.roundToInt()} km", color = TextDim, fontSize = 12.sp)
        }
    }
}

private fun ageText(ts: Long): String {
    val m = (System.currentTimeMillis() - ts) / 60000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        else -> "${m / 60} h ago"
    }
}

private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
        sin(dLon / 2) * sin(dLon / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}
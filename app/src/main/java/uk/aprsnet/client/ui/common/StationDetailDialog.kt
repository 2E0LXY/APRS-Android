package uk.aprsnet.client.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.aprs.Symbols
import uk.aprsnet.client.location.Fix
import uk.aprsnet.client.model.Station
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shared station-detail dialog - shown when a map marker is tapped or a
 * station-list row is opened. Carries the Send-message + Add-contact
 * actions, so messaging is reachable from anywhere a station appears.
 */
@Composable
fun StationDetailDialog(
    station: Station,
    myPos: Fix?,
    onSendMessage: (String) -> Unit,
    onAddContact: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                station.callsign,
                color = Accent,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("Type", color = TextDim, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    Text(
                        Symbols.describe(station.symbolTable, station.symbolCode),
                        color = TextBase, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("Position", color = TextDim, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    Text(
                        "%.4f, %.4f".format(station.lat, station.lon),
                        color = TextBase, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (myPos != null) {
                    val km = distanceKm(myPos.lat, myPos.lon, station.lat, station.lon)
                    val bearing = bearingDeg(myPos.lat, myPos.lon, station.lat, station.lon)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("Distance", color = TextDim, fontSize = 13.sp,
                            modifier = Modifier.weight(1f))
                        Text(
                            "${km.roundToInt()} km, bearing $bearing\u00B0",
                            color = TextBase, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("Last heard", color = TextDim, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    Text(
                        ageText(station.lastHeard),
                        color = TextBase, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (station.speedKmh != null && station.speedKmh > 1) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("Speed", color = TextDim, fontSize = 13.sp,
                            modifier = Modifier.weight(1f))
                        Text(
                            "%.0f km/h".format(station.speedKmh),
                            color = TextBase, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (station.path.isNotEmpty()) {
                    Spacer(Modifier.size(4.dp))
                    Text("Path", color = TextDim, fontSize = 11.sp)
                    Text(station.path, color = TextBase, fontSize = 11.sp)
                }
                if (station.comment.isNotEmpty()) {
                    Spacer(Modifier.size(4.dp))
                    Text("Comment", color = TextDim, fontSize = 11.sp)
                    Text(station.comment, color = TextBase, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSendMessage(station.callsign); onDismiss() }) {
                Icon(
                    Icons.AutoMirrored.Filled.Message,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text("Send message")
            }
        },
        dismissButton = {
            TextButton(onClick = { onAddContact(station.callsign); onDismiss() }) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text("Add contact")
            }
        }
    )
}

private fun ageText(ts: Long): String {
    val m = (System.currentTimeMillis() - ts) / 60000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 60 * 24 -> "${m / 60} h ago"
        else -> SimpleDateFormat("d MMM HH:mm", Locale.UK).format(Date(ts))
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

private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val phi1 = Math.toRadians(lat1); val phi2 = Math.toRadians(lat2)
    val lam = Math.toRadians(lon2 - lon1)
    val y = sin(lam) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(lam)
    val b = Math.toDegrees(atan2(y, x))
    return ((b + 360) % 360).roundToInt()
}
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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import uk.aprsnet.client.aprs.Symbols
import uk.aprsnet.client.location.Fix
import uk.aprsnet.client.model.Station
import uk.aprsnet.client.model.WxData
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
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                uk.aprsnet.client.ui.common.AprsSymbolIcon(
                    table = station.symbolTable,
                    code = station.symbolCode,
                    size = 32.dp
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                Text(
                    station.callsign,
                    color = Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
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
                station.wx?.let { wx ->
                    Spacer(Modifier.size(8.dp))
                    WeatherCard(wx)
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
// ---------------------------------------------------------------------------
// Weather Data card - shown when a station's last packet decoded a standard
// APRS weather report (symbol code '_'). Displays a colourful icon reflecting
// current conditions, plus the key readings.
// ---------------------------------------------------------------------------

/**
 * Returns an emoji + tint colour describing current conditions, derived from
 * temperature, rainfall and wind. Order matters: precipitation/storms take
 * priority over plain temperature so a cold rainy day reads as rain/snow,
 * not just "cold".
 */
fun weatherCondition(wx: WxData): Pair<String, Color> {
    val rain = (wx.rain1hIn ?: 0.0) > 0.0
    val windy = (wx.gustMph ?: wx.windSpeedMph ?: 0.0) >= 20.0
    val tempF = wx.tempF
    return when {
        rain && windy            -> "\u26C8\uFE0F" to Color(0xFF9333EA) // storm - purple
        rain && tempF != null && tempF <= 32.0 -> "\u2744\uFE0F" to Color(0xFF7DD3FC) // snow - light blue
        rain                      -> "\uD83C\uDF27\uFE0F" to Color(0xFF3B82F6) // rain - blue
        tempF != null && tempF <= 32.0 -> "\u2744\uFE0F" to Color(0xFF7DD3FC) // freezing - light blue
        windy                     -> "\uD83D\uDCA8" to Color(0xFF94A3B8) // windy - grey
        tempF != null && tempF >= 75.0 -> "\u2600\uFE0F" to Color(0xFFF59E0B) // hot/clear - orange
        tempF != null && tempF >= 50.0 -> "\uD83C\uDF24\uFE0F" to Color(0xFFFBBF24) // mild/sunny - amber
        else                      -> "\u26C5" to Color(0xFF94A3B8) // default - partly cloudy, grey
    }
}

/** Human-readable label matching [weatherCondition]'s classification. */
private fun weatherLabel(wx: WxData): String {
    val rain = (wx.rain1hIn ?: 0.0) > 0.0
    val windy = (wx.gustMph ?: wx.windSpeedMph ?: 0.0) >= 20.0
    val tempF = wx.tempF
    return when {
        rain && windy                          -> "Storm"
        rain && tempF != null && tempF <= 32.0 -> "Snow"
        rain                                    -> "Rain"
        tempF != null && tempF <= 32.0          -> "Freezing"
        windy                                   -> "Windy"
        tempF != null && tempF >= 75.0          -> "Hot / clear"
        tempF != null && tempF >= 50.0          -> "Mild"
        else                                    -> "Cloudy"
    }
}

@Composable
private fun WeatherCard(wx: WxData) {
    val (icon, tint) = weatherCondition(wx)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(icon, fontSize = 22.sp)
            Spacer(Modifier.size(8.dp))
            Text(
                "Weather - " + weatherLabel(wx),
                color = tint, fontWeight = FontWeight.Bold, fontSize = 14.sp
            )
        }
        Spacer(Modifier.size(6.dp))
        wx.tempF?.let { WeatherRow("Temp", "%.0f\u00B0F (%.0f\u00B0C)".format(it, (it - 32) * 5 / 9)) }
        wx.humidityPct?.let { WeatherRow("Humidity", "$it%") }
        wx.pressureHpa?.let { WeatherRow("Pressure", "%.1f hPa".format(it)) }
        if (wx.windSpeedMph != null || wx.windDirDeg != null) {
            val dir = wx.windDirDeg?.let { "$it\u00B0 " } ?: ""
            val spd = wx.windSpeedMph?.let { "%.0f mph".format(it) } ?: "n/a"
            WeatherRow("Wind", "$dir$spd")
        }
        wx.gustMph?.let { if (it > 0) WeatherRow("Gust", "%.0f mph".format(it)) }
        wx.rain1hIn?.let { if (it > 0) WeatherRow("Rain (1h)", "%.2f in".format(it)) }
        wx.rainDailyIn?.let { if (it > 0) WeatherRow("Rain (today)", "%.2f in".format(it)) }
        wx.solarWm2?.let { WeatherRow("Solar", "$it W/m\u00B2") }
        wx.uvIndex?.let { WeatherRow("UV Index", "$it") }
        wx.lightningCount?.let { if (it > 0) WeatherRow("Lightning strikes", "$it") }
    }
}

@Composable
private fun WeatherRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextBase, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

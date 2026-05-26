package uk.aprsnet.client.ui.utilities

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.aprs.AprsUtils
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.BgPanel
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim

/** Utilities: APRS-IS passcode calculator and Maidenhead converter. */
@Composable
fun UtilitiesScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        PasscodeCard()
        MaidenheadCard()
    }
}

@Composable
private fun PasscodeCard() {
    var call by remember { mutableStateOf("") }
    val code = remember(call) {
        if (call.isBlank()) "" else AprsUtils.passcode(call).toString()
    }
    Card("APRS-IS Passcode Calculator") {
        OutlinedTextField(
            value = call,
            onValueChange = { call = it.uppercase() },
            label = { Text("Callsign") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(8.dp))
        Text(
            if (code.isEmpty()) "Enter a callsign" else "Passcode: $code",
            color = if (code.isEmpty()) TextDim else Accent,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun MaidenheadCard() {
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    var grid by remember { mutableStateOf("") }

    val computedGrid = remember(lat, lon) {
        val la = lat.toDoubleOrNull()
        val lo = lon.toDoubleOrNull()
        if (la != null && lo != null) AprsUtils.toMaidenhead(la, lo) else ""
    }
    val computedCoords = remember(grid) {
        AprsUtils.fromMaidenhead(grid)
    }

    Card("Maidenhead Locator") {
        Text("Coordinates to grid", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.size(4.dp))
        Row {
            Column(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = lat, onValueChange = { lat = it },
                    label = { Text("Latitude") }, singleLine = true
                )
            }
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = lon, onValueChange = { lon = it },
                    label = { Text("Longitude") }, singleLine = true
                )
            }
        }
        if (computedGrid.isNotEmpty()) {
            Text(
                "Grid: $computedGrid",
                color = Accent, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        Spacer(Modifier.size(14.dp))
        Text("Grid to coordinates", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.size(4.dp))
        OutlinedTextField(
            value = grid, onValueChange = { grid = it },
            label = { Text("Grid locator (e.g. IO93)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (computedCoords != null) {
            Text(
                "Lat ${"%.4f".format(computedCoords.first)}, " +
                    "Lon ${"%.4f".format(computedCoords.second)}",
                color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
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
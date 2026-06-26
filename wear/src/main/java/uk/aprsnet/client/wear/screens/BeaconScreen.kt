package uk.aprsnet.client.wear.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import uk.aprsnet.client.wear.data.WearDataBridge

@Composable
fun BeaconScreen(nav: NavController) {
    val status  by WearDataBridge.status.collectAsState()
    val context    = LocalContext.current
    var beaconSent by remember { mutableStateOf(false) }

    ScalingLazyColumn(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding      = PaddingValues(vertical = 24.dp)
    ) {
        item {
            Text(
                text       = status.myCallsign.ifBlank { "—" },
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colors.primary
            )
        }

        // Position
        if (status.myLat != 0.0 || status.myLon != 0.0) {
            item {
                Text(
                    "${"%.4f".format(status.myLat)}°N",
                    fontSize = 12.sp
                )
            }
            item {
                Text(
                    "${"%.4f".format(status.myLon)}°E",
                    fontSize = 12.sp,
                    color    = Color.Gray
                )
            }
        }

        // Speed / course
        if (status.speedKmh > 0.5) {
            item {
                Text(
                    "${status.speedKmh.toInt()} km/h · ${status.course}°",
                    fontSize = 11.sp,
                    color    = Color.Gray
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }

        // Beacon Now button
        item {
            Button(
                modifier = Modifier.size(ButtonDefaults.LargeButtonSize),
                onClick  = {
                    beaconSent = true
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Wearable.getMessageClient(context)
                                .sendMessage("", WearDataBridge.MSG_BEACON, byteArrayOf())
                                .await()
                        } catch (_: Exception) {}
                    }
                },
                colors   = ButtonDefaults.buttonColors(
                    backgroundColor = if (beaconSent) Color(0xFF4CAF50) else MaterialTheme.colors.primary
                )
            ) {
                Text(if (beaconSent) "Sent ✓" else "Beacon
Now", fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        if (beaconSent) {
            item {
                Text("Sent to phone", fontSize = 10.sp, color = Color(0xFF4CAF50))
            }
        }
    }
}

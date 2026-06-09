package uk.aprsnet.client.ui.alerts

import androidx.compose.foundation.layout.*
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.BgDeep
import uk.aprsnet.client.ui.theme.AccentBlue
import uk.aprsnet.client.ui.theme.AccentRose
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim
import uk.aprsnet.client.ui.theme.TextMute

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.model.AlertRule
import uk.aprsnet.client.ui.theme.*

@Composable
fun GeoFenceScreen(vm: AprsViewModel, modifier: Modifier = Modifier) {
    val rules by vm.alertRules.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadAlertRules() }

    Scaffold(
        floatingActionButton = {
            if (vm.settings.memberSignedIn) {
                FloatingActionButton(onClick = { showAdd = true },
                    containerColor = AccentBlue) {
                    Icon(Icons.Default.Add, "Add rule", tint = BgDeep)
                }
            }
        }
    ) { padding ->
        uk.aprsnet.client.ui.common.MessageBackground(
            backgroundId = vm.settings.messageBackgroundId,
            modifier = modifier.padding(padding)
        ) {
            if (!vm.settings.memberSignedIn) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sign in to a member account to use geo-fence alerts",
                        color = TextDim, fontSize = 13.sp,
                        modifier = Modifier.padding(24.dp))
                }
            } else if (rules.isEmpty() && !loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No geo-fence rules yet", color = TextDim, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Tap + to add one", color = TextDim, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rules) { rule ->
                        GeoFenceRuleCard(rule) { vm.deleteAlertRule(rule.id) }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddGeoFenceDialog(
            onDismiss = { showAdd = false },
            onConfirm = { rule ->
                showAdd = false
                loading = true
                vm.createAlertRule(rule) { loading = false }
            }
        )
    }
}

@Composable
private fun GeoFenceRuleCard(rule: AlertRule, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = uk.aprsnet.client.ui.theme.BgCard)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = rule.name.ifEmpty { if (rule.type == "geofence_enter") "Enter zone" else "Exit zone" },
                    color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp
                )
                Spacer(Modifier.height(3.dp))
                val callLabel = if (rule.watchCallsign == "*") "Any station" else rule.watchCallsign
                val typeLabel = if (rule.type == "geofence_enter") "enters" else "leaves"
                Text("$callLabel $typeLabel a ${rule.radiusMi.toInt()} mi radius",
                    color = TextDim, fontSize = 12.sp)
                Text("${String.format("%.4f", rule.lat)}°, ${String.format("%.4f", rule.lon)}°",
                    color = TextDim, fontSize = 11.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = TextDim)
            }
        }
    }
}

@Composable
private fun AddGeoFenceDialog(onDismiss: () -> Unit, onConfirm: (AlertRule) -> Unit) {
    var name       by remember { mutableStateOf("") }
    var callsign   by remember { mutableStateOf("*") }
    var latStr     by remember { mutableStateOf("") }
    var lonStr     by remember { mutableStateOf("") }
    var radiusStr  by remember { mutableStateOf("10") }
    var ruleType   by remember { mutableStateOf("geofence_enter") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Geo-fence Rule", color = TextPrimary) },
        containerColor = BgCard,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Zone name (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = callsign,
                    onValueChange = { callsign = it.uppercase().trim() },
                    label = { Text("Watch callsign (* = any)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = latStr, onValueChange = { latStr = it },
                        label = { Text("Lat") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = lonStr, onValueChange = { lonStr = it },
                        label = { Text("Lon") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = radiusStr, onValueChange = { radiusStr = it },
                    label = { Text("Radius (miles)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Alert when station:", color = TextDim, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ruleType == "geofence_enter",
                        onClick = { ruleType = "geofence_enter" },
                        label = { Text("Enters zone") })
                    FilterChip(selected = ruleType == "geofence_exit",
                        onClick = { ruleType = "geofence_exit" },
                        label = { Text("Leaves zone") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val lat = latStr.toDoubleOrNull() ?: return@TextButton
                val lon = lonStr.toDoubleOrNull() ?: return@TextButton
                val radius = radiusStr.toDoubleOrNull() ?: 10.0
                onConfirm(AlertRule(
                    type = ruleType, watchCallsign = callsign.ifEmpty { "*" },
                    lat = lat, lon = lon, radiusMi = radius, name = name
                ))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

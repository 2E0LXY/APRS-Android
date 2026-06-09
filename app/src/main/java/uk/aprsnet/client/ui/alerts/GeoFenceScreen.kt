package uk.aprsnet.client.ui.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import uk.aprsnet.client.ui.theme.AccentBlue
import uk.aprsnet.client.ui.theme.BgDeep
import uk.aprsnet.client.ui.theme.BgPanel
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim

@Composable
fun GeoFenceScreen(vm: AprsViewModel, modifier: Modifier = Modifier) {
    val rules   by vm.alertRules.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadAlertRules() }

    Scaffold(
        containerColor = BgDeep,
        floatingActionButton = {
            if (vm.settings.memberSignedIn) {
                FloatingActionButton(
                    onClick        = { showAdd = true },
                    containerColor = AccentBlue
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add rule",
                        tint = BgDeep)
                }
            }
        }
    ) { padding ->
        uk.aprsnet.client.ui.common.MessageBackground(
            backgroundId = vm.settings.messageBackgroundId,
            modifier     = modifier.padding(padding)
        ) {
            when {
                !vm.settings.memberSignedIn -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Text("Sign in to a member account to use geo-fence alerts",
                        color = TextDim, fontSize = 13.sp,
                        modifier = Modifier.padding(24.dp))
                }
                rules.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("No geo-fence rules yet", color = TextDim, fontSize = 14.sp)
                        Text("Tap + to add one",       color = TextDim, fontSize = 12.sp)
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
            onConfirm = { rule -> showAdd = false; vm.createAlertRule(rule) {} }
        )
    }
}

@Composable
private fun GeoFenceRuleCard(rule: AlertRule, onDelete: () -> Unit) {
    Surface(
        modifier      = Modifier.fillMaxWidth(),
        color         = BgPanel,
        shape         = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    rule.name.ifEmpty {
                        if (rule.type == "geofence_enter") "Enter zone" else "Exit zone"
                    },
                    color      = TextBase,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp
                )
                val callLabel = if (rule.watchCallsign == "*") "Any station"
                                else rule.watchCallsign
                val typeLabel = if (rule.type == "geofence_enter") "enters" else "leaves"
                Text("$callLabel $typeLabel a ${rule.radiusMi.toInt()} mi radius",
                    color = TextDim, fontSize = 12.sp)
                Text(
                    "${String.format("%.4f", rule.lat)}°, ${String.format("%.4f", rule.lon)}°",
                    color = TextDim, fontSize = 11.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete rule",
                    tint = TextDim)
            }
        }
    }
}

@Composable
private fun AddGeoFenceDialog(
    onDismiss: () -> Unit,
    onConfirm: (AlertRule) -> Unit
) {
    var name      by remember { mutableStateOf("") }
    var callsign  by remember { mutableStateOf("*") }
    var latStr    by remember { mutableStateOf("") }
    var lonStr    by remember { mutableStateOf("") }
    var radiusStr by remember { mutableStateOf("10") }
    var ruleType  by remember { mutableStateOf("geofence_enter") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgPanel,
        title  = { Text("Add Geo-fence Rule", color = TextBase) },
        text   = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Zone name (optional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = callsign,
                    onValueChange = { callsign = it.uppercase().trim() },
                    label = { Text("Watch callsign (* = any)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = latStr, onValueChange = { latStr = it },
                        label = { Text("Lat") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lonStr, onValueChange = { lonStr = it },
                        label = { Text("Lon") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = radiusStr, onValueChange = { radiusStr = it },
                    label = { Text("Radius (miles)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("Alert when station:", color = TextDim, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ruleType == "geofence_enter",
                        onClick  = { ruleType = "geofence_enter" },
                        label    = { Text("Enters zone") }
                    )
                    FilterChip(
                        selected = ruleType == "geofence_exit",
                        onClick  = { ruleType = "geofence_exit" },
                        label    = { Text("Leaves zone") }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val lat    = latStr.toDoubleOrNull()    ?: return@TextButton
                val lon    = lonStr.toDoubleOrNull()    ?: return@TextButton
                val radius = radiusStr.toDoubleOrNull() ?: 10.0
                onConfirm(AlertRule(
                    type          = ruleType,
                    watchCallsign = callsign.ifEmpty { "*" },
                    lat           = lat, lon = lon,
                    radiusMi      = radius, name = name
                ))
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

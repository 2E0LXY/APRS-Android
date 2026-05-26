package uk.aprsnet.client.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.BgPanel
import uk.aprsnet.client.ui.theme.Ok
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim

/**
 * Settings - the screen where the user enters their callsign and APRS-IS
 * passcode (which unlocks transmitting messages and position beacons),
 * plus position mode and beacon options.
 */
@Composable
fun SettingsScreen(vm: AprsViewModel, modifier: Modifier = Modifier) {
    val s = vm.settings

    var callsign by remember { mutableStateOf(s.callsign) }
    var passcode by remember { mutableStateOf(s.passcode) }
    var posMode by remember { mutableStateOf(s.positionMode) }
    var comment by remember { mutableStateOf(s.beaconComment) }
    var symTable by remember { mutableStateOf(s.symbolTable) }
    var symCode by remember { mutableStateOf(s.symbolCode) }
    var saved by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Card("APRS Credentials") {
            Text(
                "Your callsign and APRS-IS passcode. Required to send " +
                    "messages and beacon your position.",
                color = TextDim, fontSize = 12.sp
            )
            Spacer(Modifier.size(8.dp))
            Field("Callsign", callsign) { callsign = it.uppercase() }
            Field(
                "APRS-IS passcode", passcode, password = true,
                numeric = true
            ) { passcode = it }
        }

        Card("Position / Beaconing") {
            Text("Position mode", color = TextDim, fontSize = 12.sp)
            Spacer(Modifier.size(6.dp))
            Row {
                ModeChip("Smart", posMode == "smart") { posMode = "smart" }
                Spacer(Modifier.size(8.dp))
                ModeChip("Manual", posMode == "manual") { posMode = "manual" }
                Spacer(Modifier.size(8.dp))
                ModeChip("Off", posMode == "off") { posMode = "off" }
            }
            Spacer(Modifier.size(10.dp))
            Field("Beacon comment", comment) { comment = it }
            Row {
                Column(Modifier.weight(1f)) {
                    Field("Symbol table", symTable) {
                        symTable = it.take(1)
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Field("Symbol code", symCode) {
                        symCode = it.take(1)
                    }
                }
            }
            Text(
                "Smart beaconing sends your position more often when " +
                    "moving fast, less when still.",
                color = TextDim, fontSize = 11.sp
            )
        }

        Button(
            onClick = {
                s.callsign = callsign
                s.passcode = passcode
                s.positionMode = posMode
                s.beaconComment = comment
                s.symbolTable = symTable.ifEmpty { "/" }
                s.symbolCode = symCode.ifEmpty { ">" }
                vm.applySettings()
                saved = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text("Save settings")
        }
        if (saved) {
            Text(
                "Saved - reconnecting with your callsign.",
                color = Ok, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
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

@Composable
private fun Field(
    label: String,
    value: String,
    password: Boolean = false,
    numeric: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation()
                               else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) TextBase else TextDim,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent.copy(alpha = 0.25f) else BgPanel)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
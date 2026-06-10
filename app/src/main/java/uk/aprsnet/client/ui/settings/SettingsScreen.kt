package uk.aprsnet.client.ui.settings
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.ui.alerts.GeoFenceScreen
import uk.aprsnet.client.net.AprsWebSocket
import uk.aprsnet.client.ui.common.GlassCard
import uk.aprsnet.client.ui.common.RingProgress
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.AccentAmber
import uk.aprsnet.client.ui.theme.AccentLime
import uk.aprsnet.client.ui.theme.AccentPurple
import uk.aprsnet.client.ui.theme.BgPanel
import uk.aprsnet.client.ui.theme.BorderCol
import uk.aprsnet.client.ui.theme.Err
import uk.aprsnet.client.ui.theme.Ok
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim
import uk.aprsnet.client.ui.theme.TextHi


/**
 * Comprehensive user-settings hub. Every preference the app exposes lives
 * here: website account, APRS credentials, position/beaconing, map filters,
 * notifications and quiet hours. The Admin screen (server admin login) is
 * a separate screen behind the More menu.
 */
@Composable
fun SettingsScreen(vm: AprsViewModel, modifier: Modifier = Modifier, onNavigateToGeoFence: () -> Unit = {}) {
    // Bumped by MemberAccountCard after a successful login so the
    // sibling AprsCredentialsCard re-reads call/pass/ssid from settings.
    var credentialsRefresh by remember { mutableStateOf(0) }
    var showGeoFence by remember { mutableStateOf(false) }
    if (showGeoFence) {
        GeoFenceScreen(vm = vm, modifier = modifier)
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        AppearanceCard(vm)
        MemberAccountCard(vm, onCredentialsLoaded = { credentialsRefresh++ })
        AprsCredentialsCard(vm, refreshKey = credentialsRefresh)
        PositionCard(vm)
        FiltersCard(vm)
        AisCard(vm)
        NotificationsCard(vm)
        StatusSection(vm)
        HelpCard()
        CloseAppCard()
    }
}

// ============================================================================
// Website member account
// ============================================================================
@Composable
private fun MemberAccountCard(vm: AprsViewModel, onCredentialsLoaded: () -> Unit) {
    val s = vm.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(s.memberSignedIn) }
    var name by remember { mutableStateOf(s.memberName) }
    var callsign by remember { mutableStateOf(s.callsign) }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var statusIsError by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    Card("Website Member Account") {
        Text(
            "You must register an account at www.aprsnet.uk FIRST. " +
                "Signing in here lets the app fetch your passcode " +
                "automatically and keeps your messages in sync between " +
                "the website and this app (store-and-forward).",
            color = TextDim, fontSize = 12.sp
        )
        Spacer(Modifier.size(8.dp))
        OutlinedButton(
            onClick = {
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.aprsnet.uk/setup"))
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Open www.aprsnet.uk to register / manage account") }
        Spacer(Modifier.size(12.dp))

        if (signedIn) {
            Text(
                "Signed in as ${name.ifEmpty { "(account)" }}",
                color = Ok, fontWeight = FontWeight.Bold, fontSize = 14.sp
            )
            Spacer(Modifier.size(6.dp))
            OutlinedButton(
                onClick = {
                    vm.signOutMember()
                    signedIn = false
                    name = ""
                    status = "Signed out"
                    statusIsError = false
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Sign out") }
        } else {
            OutlinedTextField(
                value = callsign,
                onValueChange = { callsign = it.uppercase() },
                label = { Text("Callsign") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Website password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            Button(
                onClick = {
                    if (callsign.isBlank() || password.isBlank()) {
                        status = "Enter your callsign and password"
                        statusIsError = true
                        return@Button
                    }
                    working = true
                    status = "Signing in..."
                    statusIsError = false
                    scope.launch {
                        val err = vm.loginMember(callsign, password)
                        working = false
                        if (err == null) {
                            signedIn = true
                            name = vm.settings.memberName
                            status = "Signed in - passcode loaded automatically"
                            statusIsError = false
                            password = ""
                            // Refresh APRS Credentials card so the auto-filled
                            // callsign + passcode become visible there.
                            onCredentialsLoaded()
                        } else {
                            status = err
                            statusIsError = true
                        }
                    }
                },
                enabled = !working,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) { Text(if (working) "Signing in..." else "Sign in") }
        }
        if (status.isNotEmpty()) {
            Text(
                status,
                color = if (statusIsError) Err else Ok,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// ============================================================================
// APRS credentials (manual entry alternative to member sign-in)
// ============================================================================
@Composable
private fun AprsCredentialsCard(vm: AprsViewModel, refreshKey: Int) {
    val s = vm.settings
    var call by remember(refreshKey) { mutableStateOf(s.callsign) }
    var pass by remember(refreshKey) { mutableStateOf(s.passcode) }
    var ssid by remember(refreshKey) { mutableStateOf(s.ssid) }
    var saved by remember { mutableStateOf(false) }

    Card("APRS Credentials") {
        Text(
            "These are populated automatically when you sign in to your " +
                "member account above. Or enter them manually. They are " +
                "stored on this device only.",
            color = TextDim, fontSize = 12.sp
        )
        Spacer(Modifier.size(6.dp))
        OutlinedTextField(
            value = call, onValueChange = { call = it.uppercase() },
            label = { Text("Callsign") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("APRS-IS passcode") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        SsidPicker(
            current = ssid,
            onChange = { ssid = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        Button(
            onClick = {
                s.callsign = call
                s.passcode = pass
                s.ssid = ssid
                vm.applySettings()
                saved = true
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) { Text("Save credentials") }
        if (saved) {
            Text(
                "Saved - reconnecting WebSocket with your callsign.",
                color = Ok, fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// ============================================================================
// Position / beaconing
// ============================================================================
@Composable
private fun PositionCard(vm: AprsViewModel) {
    val s = vm.settings
    var mode by remember { mutableStateOf(s.positionMode) }
    var comment by remember { mutableStateOf(s.beaconComment) }
    var symT by remember { mutableStateOf(s.symbolTable) }
    var symC by remember { mutableStateOf(s.symbolCode) }
    var statusTxt by remember { mutableStateOf(s.statusText) }
    var saved by remember { mutableStateOf(false) }

    Card("Position / Beaconing") {
        Text("Position mode", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.size(6.dp))
        Row {
            ModeChip("Smart", mode == "smart") { mode = "smart" }
            Spacer(Modifier.size(8.dp))
            ModeChip("Manual", mode == "manual") { mode = "manual" }
            Spacer(Modifier.size(8.dp))
            ModeChip("Off", mode == "off") { mode = "off" }
        }
        Spacer(Modifier.size(10.dp))
        Text(
            "Beacon comment (shown on aprs.fi against your station)",
            color = TextDim, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        OutlinedTextField(
            value = comment, onValueChange = { comment = it },
            label = { Text("Beacon comment") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        Text(
            "Status text (sent as a separate APRS status packet alongside each beacon)",
            color = TextDim, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        OutlinedTextField(
            value = statusTxt, onValueChange = { statusTxt = it },
            label = { Text("Status (optional)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        Text(
            "APRS symbol (shown on aprs.fi and on other clients' maps)",
            color = TextDim, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        SymbolPicker(
            currentTable = symT.firstOrNull() ?: '/',
            currentCode  = symC.firstOrNull() ?: '>',
            onChange = { t, c -> symT = t.toString(); symC = c.toString() }
        )
        Text(
            "Smart beaconing sends position more often when moving fast, " +
                "less often when still. Requires location permission.",
            color = TextDim, fontSize = 11.sp
        )
        Button(
            onClick = {
                s.positionMode = mode
                s.beaconComment = comment
                s.statusText = statusTxt
                s.symbolTable = symT.ifEmpty { "/" }
                s.symbolCode = symC.ifEmpty { ">" }
                vm.applySettings()
                saved = true
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) { Text("Save position settings") }
        if (saved) Text("Saved", color = Ok, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp))
    }
}

// ============================================================================
// Map / station-list filters
// ============================================================================
@Composable
private fun FiltersCard(vm: AprsViewModel) {
    val s = vm.settings
    var ham by remember { mutableStateOf(s.showHam) }
    var weather by remember { mutableStateOf(s.showWeather) }
    var glider by remember { mutableStateOf(s.showGlider) }
    var ship by remember { mutableStateOf(s.showShip) }
    var lora by remember { mutableStateOf(s.showLora) }
    var mmdvm by remember { mutableStateOf(s.showMmdvm) }
    var other by remember { mutableStateOf(s.showOther) }
    var dropPi by remember { mutableStateOf(s.dropPistar) }
    var dropD  by remember { mutableStateOf(s.dropDstar) }
    var dropDk by remember { mutableStateOf(s.dropApdesk) }

    Card("Map Filters") {
        Text("Choose which station types appear on the map and in the " +
            "stations list. Changes apply immediately.",
            color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.size(6.dp))
        FilterRow("Ham (APRS)", ham) { ham = it; s.showHam = it; vm.tickFilters() }
        FilterRow("Weather (CWOP)", weather) { weather = it; s.showWeather = it; vm.tickFilters() }
        FilterRow("Gliders (OGN)", glider) { glider = it; s.showGlider = it; vm.tickFilters() }
        FilterRow("Ships / boats", ship) { ship = it; s.showShip = it; vm.tickFilters() }
        FilterRow("LoRa", lora) { lora = it; s.showLora = it; vm.tickFilters() }
        FilterRow("MMDVM (DMR / D-STAR / YSF)", mmdvm) { mmdvm = it; s.showMmdvm = it; vm.tickFilters() }
        FilterRow("Other (objects, repeaters, digis, unclassified)", other) { other = it; s.showOther = it; vm.tickFilters() }

        Spacer(Modifier.size(12.dp))
        Text("Hide categories (synced with member account on aprsnet.uk)",
            color = TextDim, fontSize = 12.sp)
        Text("Toggling any of these here also pushes the change to your " +
            "web map account, and vice-versa on next login.",
            color = TextDim, fontSize = 10.sp)
        Spacer(Modifier.size(4.dp))
        FilterRow("Drop digital-voice gateways (Pi-Star / MMDVM / DMRGateway / ircDDB)",
            dropPi) { dropPi = it; s.dropPistar = it; vm.tickFilters(); vm.pushMemberPreferences() }
        FilterRow("Drop D-STAR routes",
            dropD)  { dropD  = it; s.dropDstar  = it; vm.tickFilters(); vm.pushMemberPreferences() }
        FilterRow("Drop APDESK status beacons",
            dropDk) { dropDk = it; s.dropApdesk = it; vm.tickFilters(); vm.pushMemberPreferences() }
    }
}

@Composable
private fun FilterRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextBase, fontSize = 14.sp,
            modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

// ============================================================================
// Notifications + quiet hours
// ============================================================================
@Composable
private fun NotificationsCard(vm: AprsViewModel) {
    val s = vm.settings
    var msg by remember { mutableStateOf(s.notifyMessages) }
    var wx by remember { mutableStateOf(s.notifyWeather) }
    var quiet by remember { mutableStateOf(s.quietHoursEnabled) }
    var qStart by remember { mutableStateOf(s.quietStart.toString()) }
    var qEnd by remember { mutableStateOf(s.quietEnd.toString()) }

    Card("Notifications") {
        FilterRow("Incoming messages", msg) { msg = it; s.notifyMessages = it }
        FilterRow("Severe weather warnings", wx) { wx = it; s.notifyWeather = it }
        Spacer(Modifier.size(6.dp))
        FilterRow("Quiet hours", quiet) { quiet = it; s.quietHoursEnabled = it }
        if (quiet) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                OutlinedTextField(
                    value = qStart,
                    onValueChange = {
                        qStart = it.filter(Char::isDigit).take(2)
                        qStart.toIntOrNull()?.let { v -> s.quietStart = v }
                    },
                    label = { Text("Start (0-23)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = qEnd,
                    onValueChange = {
                        qEnd = it.filter(Char::isDigit).take(2)
                        qEnd.toIntOrNull()?.let { v -> s.quietEnd = v }
                    },
                    label = { Text("End (0-23)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Inside quiet hours, notifications are silent (still posted, " +
                    "no sound/vibrate).",
                color = TextDim, fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ============================================================================
// AIS / Ships (direct aisstream.io connection)
// ============================================================================
@Composable
private fun AisCard(vm: AprsViewModel) {
    val s   = vm.settings
    val ctx = LocalContext.current
    var key        by remember { mutableStateOf(s.aisApiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var saved      by remember { mutableStateOf(false) }

    Card("AIS / Ships (direct)") {
        Text(
            "Optional: enter an aisstream.io API key to receive live maritime " +
            "AIS vessel positions directly on this device. Leave blank to rely " +
            "on the server relay (if configured). Free tier allows ONE " +
            "WebSocket connection per key â€” do not use the same key as the server.",
            color = TextDim, fontSize = 12.sp
        )
        Spacer(Modifier.size(8.dp))
        OutlinedButton(
            onClick = {
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://aisstream.io"))
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Get a free API key at aisstream.io") }
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.trim(); saved = false },
            label = { Text("aisstream.io API key (optional)") },
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None
                                   else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { keyVisible = !keyVisible }) {
                    Text(if (keyVisible) "Hide" else "Show",
                        fontSize = 11.sp, color = TextDim)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        )
        Button(
            onClick = {
                s.aisApiKey = key
                vm.restartAis()
                saved = true
            },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Text(if (key.isBlank()) "Save (no key - direct AIS disabled)"
                 else "Save & connect")
        }
        if (saved) {
            Text(
                if (key.isBlank()) "Direct AIS disabled â€” relying on server relay."
                else "Connecting to aisstream.io â€” vessels will appear shortly.",
                color = if (key.isBlank()) TextDim else Ok,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

// ============================================================================
// Geo-fence alert rules section
// ============================================================================
@Composable
private fun GeoFenceSection(vm: AprsViewModel, onNavigate: () -> Unit) {
    Card("Geo-fence Alerts") {
        if (!vm.settings.memberSignedIn) {
            Text(
                "Sign in to your aprsnet.uk member account in the Member Account " +
                "section above to set up geo-fence alert rules.",
                color = TextDim, fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            return@Card
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onNavigate)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Notify when a station enters or leaves an area",
                color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("›", color = TextDim, fontSize = 20.sp)
        }
    }
}

// ============================================================================
// shared bits
// ============================================================================
@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    GlassCard(title = title, content = content)
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

// ============================================================================
// SSID picker - 0..15 with the conventional role label per APRS spec
// ============================================================================
@Composable
private fun SsidPicker(
    current: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text("SSID (callsign suffix)", color = TextDim, fontSize = 12.sp)
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Text(
                "-$current  ${SSID_LABELS[current]}",
                color = TextBase
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (i in 0..15) {
                DropdownMenuItem(
                    text = { Text("-$i  ${SSID_LABELS[i]}") },
                    onClick = { onChange(i); open = false }
                )
            }
        }
    }
}

private val SSID_LABELS = arrayOf(
    "Base / HF",          //  0
    "Generic 1",          //  1
    "Generic 2",          //  2
    "Generic 3",          //  3
    "HF gateway",         //  4
    "IGate / web",        //  5
    "Satellite / IOTA",   //  6
    "HT (handheld)",      //  7
    "Boat / sailboat",    //  8
    "Mobile / car",       //  9
    "Internet / Echolink",// 10
    "Balloon / aircraft", // 11
    "DTMF / POS / RFID",  // 12
    "Weather station",    // 13
    "Truck / RV",         // 14
    "Generic 15"          // 15
)

// ============================================================================
// Appearance - theme picker + outgoing bubble colour picker
// ============================================================================
@Composable
private fun AppearanceCard(vm: AprsViewModel) {
    val s = vm.settings
    var themeId by remember { mutableStateOf(s.themeId) }
    var bubbleId by remember { mutableStateOf(s.bubbleColourId) }
    var incomingBubbleId by remember { mutableStateOf(s.incomingBubbleColourId) }

    GlassCard(title = "Appearance") {
        Text(
            "Pick a background theme and a colour for your outgoing message " +
                "bubbles. ACKed messages stay green for clarity.",
            color = TextDim, fontSize = 12.sp
        )
        Spacer(Modifier.size(10.dp))

        // -- theme row -------------------------------------------------------
        Text("Background", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.size(6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            uk.aprsnet.client.ui.theme.APP_THEMES.forEach { t ->
                ThemeSwatch(
                    label = t.label,
                    topColour = t.gradientTop,
                    bottomColour = t.gradientBottom,
                    selected = themeId == t.id,
                    onClick = {
                        themeId = t.id
                        s.themeId = t.id
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                )
            }
        }

        Spacer(Modifier.size(12.dp))

        // -- bubble-colour row ----------------------------------------------
        Text("Outgoing message bubble", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.size(6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            uk.aprsnet.client.ui.theme.BUBBLE_PALETTES.forEach { p ->
                BubbleSwatch(
                    topColour = p.top,
                    bottomColour = p.bottom,
                    selected = bubbleId == p.id,
                    onClick = {
                        bubbleId = p.id
                        s.bubbleColourId = p.id
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                )
            }
        }
        Text(
            "Changes apply instantly. ACKed bubbles stay lime/green.",
            color = TextDim, fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(Modifier.size(12.dp))

        // -- incoming bubble-colour row -------------------------------------
        Text("Incoming message bubble", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.size(6.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            uk.aprsnet.client.ui.theme.BUBBLE_PALETTES.forEach { p ->
                BubbleSwatch(
                    topColour = p.top,
                    bottomColour = p.bottom,
                    selected = incomingBubbleId == p.id,
                    onClick = {
                        incomingBubbleId = p.id
                        s.incomingBubbleColourId = p.id
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
                )
            }
        }

        // -- Message section background picker -----------------------------
        Spacer(Modifier.size(14.dp))
        Text("Messages background", color = TextDim, fontSize = 12.sp)
        Spacer(Modifier.size(6.dp))
        var msgBgId by remember { mutableStateOf(s.messageBackgroundId) }
        Column(modifier = Modifier.fillMaxWidth()) {
            uk.aprsnet.client.ui.common.MESSAGE_BG_NAMES.forEachIndexed { idx, label ->
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            msgBgId = idx
                            s.messageBackgroundId = idx
                        }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = msgBgId == idx,
                        onClick = {
                            msgBgId = idx
                            s.messageBackgroundId = idx
                        }
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(label, color = TextBase, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(
    label: String,
    topColour: androidx.compose.ui.graphics.Color,
    bottomColour: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(topColour, bottomColour)
                    )
                )
                .border(
                    androidx.compose.foundation.BorderStroke(
                        if (selected) 2.dp else 1.dp,
                        if (selected) Accent else BorderCol
                    ),
                    RoundedCornerShape(8.dp)
                )
                .clickable(onClick = onClick)
        )
        Text(
            label,
            color = if (selected) Accent else TextDim,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun BubbleSwatch(
    topColour: androidx.compose.ui.graphics.Color,
    bottomColour: androidx.compose.ui.graphics.Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(topColour, bottomColour)
                )
            )
            .border(
                androidx.compose.foundation.BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) Accent else BorderCol
                ),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
    )
}
// ============================================================================
// SymbolPicker - curated grid of common APRS symbols + current-selection
// preview. Replaces the previous two text-field arrangement which gave no
// visual feedback. Tapping a swatch updates symT/symC immediately - the
// user still hits 'Save position settings' to persist.
// ============================================================================
private data class SymbolChoice(val table: Char, val code: Char, val label: String)

private val SYMBOL_CHOICES = listOf(
    SymbolChoice('/', '-', "QTH"),
    SymbolChoice('/', '>', "Car"),
    SymbolChoice('/', '<', "M/cycle"),
    SymbolChoice('/', 'b', "Bike"),
    SymbolChoice('/', '[', "Jogger"),
    SymbolChoice('/', 'k', "Truck"),
    SymbolChoice('/', 'u', "18W"),
    SymbolChoice('/', 'R', "RV"),
    SymbolChoice('/', 'v', "Van"),
    SymbolChoice('/', 'Y', "Yacht"),
    SymbolChoice('/', 's', "Ship"),
    SymbolChoice('/', 'C', "Canoe"),
    SymbolChoice('/', '^', "Aircraft"),
    SymbolChoice('/', '\'', "Sm. air"),
    SymbolChoice('/', 'X', "Heli"),
    SymbolChoice('/', 'O', "Balloon"),
    SymbolChoice('/', 'g', "Glider"),
    SymbolChoice('/', '_', "WX"),
    SymbolChoice('/', '#', "Digi"),
    SymbolChoice('/', '&', "IGate"),
    SymbolChoice('\\', 'r', "Repeat"),
    SymbolChoice('/', 'H', "Hotel"),
    SymbolChoice('/', 'h', "Hosp"),
    SymbolChoice('/', ';', "Camp")
)

@Composable
private fun SymbolPicker(
    currentTable: Char,
    currentCode: Char,
    onChange: (Char, Char) -> Unit
) {
    // Current selection preview - sprite + table/code/label
    val currentChoice = SYMBOL_CHOICES.firstOrNull {
        it.table == currentTable && it.code == currentCode
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        uk.aprsnet.client.ui.common.AprsSymbolIcon(
            table = currentTable, code = currentCode, size = 40.dp)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Current: $currentTable $currentCode" +
                    (currentChoice?.let { "  -  ${it.label}" } ?: "  -  custom"),
                color = TextBase, fontWeight = FontWeight.Bold, fontSize = 14.sp
            )
            Text("Tap a swatch below to change", color = TextDim, fontSize = 11.sp)
        }
    }

    // 6-column grid via Column-of-Rows (works inside scrolling parent;
    // LazyVerticalGrid would conflict with the surrounding scroll).
    val cols = 6
    SYMBOL_CHOICES.chunked(cols).forEach { row ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            row.forEach { choice ->
                val selected = choice.table == currentTable && choice.code == currentCode
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .clickable { onChange(choice.table, choice.code) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(40.dp)
                            .then(
                                if (selected)
                                    Modifier.border(
                                        width = 2.dp,
                                        color = Accent,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ).padding(2.dp)
                                else Modifier
                            )
                    ) {
                        uk.aprsnet.client.ui.common.AprsSymbolIcon(
                            table = choice.table, code = choice.code, size = 36.dp)
                    }
                    Text(
                        choice.label,
                        color = if (selected) Accent else TextDim,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            // Pad short final row so cells stay aligned
            repeat(cols - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// ============================================================================
// Status section - appended below settings cards (mirrors StatusScreen content)
// ============================================================================
@Composable
private fun StatusSection(vm: AprsViewModel) {
    val status by vm.status.collectAsState()
    val conn   by vm.connState.collectAsState()
    val stations by vm.stations.collectAsState()
    val lastBeaconAt by vm.beacon.lastBeaconAt.collectAsState()
    val myFix  by vm.myPosition.collectAsState()

    // Ring gauges
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val s = status
            RingProgress(
                fraction = (stations.size / 1000f).coerceAtMost(1f),
                value = stations.size.toString(),
                label = "ON MAP",
                gradient = listOf(Accent, AccentLime, Accent)
            )
            RingProgress(
                fraction = if (s != null && s.upstreamConnected) 1f else 0f,
                value = if (s != null && s.upstreamConnected) "UP" else "DOWN",
                label = "UPSTREAM",
                gradient = listOf(AccentLime, Accent, AccentLime)
            )
            RingProgress(
                fraction = when (conn) {
                    AprsWebSocket.ConnState.AUTHED       -> 1f
                    AprsWebSocket.ConnState.CONNECTED    -> 0.7f
                    AprsWebSocket.ConnState.CONNECTING   -> 0.3f
                    AprsWebSocket.ConnState.DISCONNECTED -> 0f
                },
                value = when (conn) {
                    AprsWebSocket.ConnState.AUTHED       -> "AUTH"
                    AprsWebSocket.ConnState.CONNECTED    -> "CONN"
                    AprsWebSocket.ConnState.CONNECTING   -> "..."
                    AprsWebSocket.ConnState.DISCONNECTED -> "OFF"
                },
                label = "WS",
                gradient = listOf(AccentPurple, Accent, AccentPurple)
            )
        }
    }

    // Connection card
    GlassCard(title = "Connection") {
        StatusRow("WebSocket", when (conn) {
            AprsWebSocket.ConnState.AUTHED       -> "Connected (authenticated)"
            AprsWebSocket.ConnState.CONNECTED    -> "Connected"
            AprsWebSocket.ConnState.CONNECTING   -> "Connecting..."
            AprsWebSocket.ConnState.DISCONNECTED -> "Disconnected"
        }, valueColour = when (conn) {
            AprsWebSocket.ConnState.AUTHED       -> Ok
            AprsWebSocket.ConnState.DISCONNECTED -> Err
            else                                 -> TextBase
        })
        StatusRow("Stations on map", stations.size.toString())
    }

    // Beacon card
    GlassCard(title = "Position beacon") {
        val mode = vm.settings.positionMode
        StatusRow("Mode", mode.replaceFirstChar { it.uppercase() },
            valueColour = if (mode == "smart") Ok else AccentAmber)
        StatusRow("Have GPS fix?", if (myFix == null) "No" else "Yes",
            valueColour = if (myFix == null) Err else Ok)
        StatusRow("Last beacon", statusBeaconAge(lastBeaconAt))
    }

    // Server card
    GlassCard(title = "Server") {
        val s = status
        if (s == null) {
            Text("Loading server status...", color = TextDim, fontSize = 13.sp)
        } else {
            StatusRow("Uptime", s.uptime)
            StatusRow("Packets received", s.packetsRx.toString())
            StatusRow("Upstream", if (s.upstreamConnected) "Connected" else "Down",
                valueColour = if (s.upstreamConnected) Ok else Err)
            StatusRow("Server stations", s.stations.toString())
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueColour: androidx.compose.ui.graphics.Color = TextBase
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColour, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    }
}

private fun statusBeaconAge(ts: Long): String {
    if (ts <= 0L) return "Never"
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000     -> "${diff / 1000}s ago"
        diff < 3_600_000  -> "${diff / 60_000}m ago"
        else              -> "${diff / 3_600_000}h ago"
    }
}

// ============================================================================
// Help Card
// ============================================================================
@Composable
private fun HelpCard() {
    var expanded by remember { mutableStateOf(false) }

    GlassCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.HelpOutline, contentDescription = null,
                    tint = Accent, modifier = Modifier.size(20.dp))
                Text("Help & Instructions", color = TextHi,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null, tint = TextDim
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HelpSection("Getting Started") {
                    HelpItem("1.", "Tap Settings (gear icon) and enter your callsign and APRS-IS passcode under Credentials.")
                    HelpItem("2.", "Set Position / Beaconing mode to Smart. Enter a beacon comment and optionally a status text.")
                    HelpItem("3.", "Tap Save. The map will populate with live stations within seconds.")
                    HelpItem("Note", "Receiving works without credentials. Sending messages and beaconing require a valid callsign/passcode pair.")
                }

                HorizontalDivider(color = TextDim.copy(alpha = 0.2f))

                HelpSection("Map") {
                    HelpItem("Markers", "Every heard APRS station appears as a symbol on the map. Tap any marker to open the station detail dialog showing type, position, distance, bearing, path, and comment.")
                    HelpItem("My Location", "The purple button (bottom-right) centres the map on your GPS fix. Long-press it to beacon your position immediately.")
                    HelpItem("Filter", "The funnel button (bottom-left) opens the quick-filter panel. Toggle station types on/off and set a distance limit. Changes apply instantly.")
                    HelpItem("Ships", "Live AIS maritime vessels are streamed from aisstream.io via the server and appear as ship markers when Ships is enabled.")
                    HelpItem("Clusters", "At low zoom levels, nearby stations are grouped into numbered cluster bubbles. Tap to zoom in and expand.")
                }

                HorizontalDivider(color = TextDim.copy(alpha = 0.2f))

                HelpSection("Filters") {
                    HelpItem("HAM", "Standard amateur radio / APRS stations.")
                    HelpItem("WX", "CWOP weather stations (wind, temperature, rain).")
                    HelpItem("Ships", "AIS maritime vessels and APRS-IS ship objects.")
                    HelpItem("Gliders", "OGN glider and light aircraft trackers.")
                    HelpItem("LoRa", "LoRa-APRS digipeaters, trackers, and iGates.")
                    HelpItem("MMDVM", "MMDVM and Pistar hotspots (DMR, D-STAR, YSF).")
                    HelpItem("Other", "Objects, repeaters, digipeaters, and unclassified stations.")
                    HelpItem("Distance", "Only show stations within the selected radius of your GPS fix. Set to All to show everything regardless of range.")
                }

                HorizontalDivider(color = TextDim.copy(alpha = 0.2f))

                HelpSection("Messaging") {
                    HelpItem("Send", "Tap a station marker → Send message in the detail dialog. Or tap the Message tab → tap a conversation.")
                    HelpItem("ACK", "Outgoing message bubbles turn green when the recipient ACKs the message. Unacknowledged messages are retried automatically.")
                    HelpItem("Incoming", "Incoming messages trigger a notification. Tap it to open the conversation.")
                    HelpItem("Limit", "APRS messages are limited to 67 characters. The character counter shows remaining space.")
                }

                HorizontalDivider(color = TextDim.copy(alpha = 0.2f))

                HelpSection("Beaconing") {
                    HelpItem("Smart", "Beacons frequently when moving, slows when stationary. Recommended for mobiles and trackers.")
                    HelpItem("Fixed", "Beacons at a fixed interval regardless of movement.")
                    HelpItem("Off", "Disables position beaconing entirely.")
                    HelpItem("Comment", "Short text appended to each position beacon — visible on aprs.fi and other APRS clients.")
                    HelpItem("Status", "Sent as a separate APRS status packet (>) alongside each position beacon.")
                    HelpItem("Symbol", "The APRS symbol shown on maps. Use the symbol table and code fields to customise.")
                    HelpItem("Background", "Beaconing continues in the background via a foreground service. Do not force-stop the app.")
                }

                HorizontalDivider(color = TextDim.copy(alpha = 0.2f))

                HelpSection("Connection") {
                    HelpItem("WS State", "AUTH = connected and authenticated, CONN = connected but not yet authed, ... = connecting, OFF = disconnected.")
                    HelpItem("Auto-reconnect", "The app reconnects automatically after any disconnect. No manual action needed.")
                    HelpItem("Passcode", "Your APRS-IS passcode is calculated from your callsign. Use Utilities → Passcode Calculator if you do not know yours.")
                }
            }
        }
    }
}

@Composable
private fun HelpSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun HelpItem(label: String, text: String) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = TextDim, fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.widthIn(min = 52.dp))
        Text(text, color = TextBase, fontSize = 12.sp,
            modifier = Modifier.weight(1f))
    }
}

// ============================================================================
// Close App Card
// ============================================================================
@Composable
private fun CloseAppCard() {
    val context = LocalContext.current
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Close App", color = TextHi,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Stops all background services and exits completely.",
                    color = TextDim, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
            Button(
                onClick = {
                    (context as? Activity)?.finishAffinity()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Err.copy(alpha = 0.85f)
                )
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Close", fontSize = 13.sp)
            }
        }
    }
}

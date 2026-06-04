package uk.aprsnet.client.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import uk.aprsnet.client.net.AprsApi
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.AccentRose
import uk.aprsnet.client.ui.theme.BgPanel
import uk.aprsnet.client.ui.theme.Err
import uk.aprsnet.client.ui.theme.Ok
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Admin: authenticate with the server admin credentials, then drive the full
 * set of admin actions natively without leaving the app. Sections covered:
 *   - Server Configuration (read-only key/value)
 *   - Message of the Day (enable toggle + edit + save)
 *   - Members (read-only list with login/session counts)
 *   - Bans (list + add by callsign+reason + remove)
 *   - Audit log (last 50 entries)
 */
@Composable
fun AdminScreen(modifier: Modifier = Modifier) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        if (!loggedIn) {
            Card("Admin Login") {
                Text(
                    "Sign in with the server admin credentials to view and " +
                        "edit MOTD, members, bans, and audit log.",
                    color = TextDim, fontSize = 12.sp
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = user, onValueChange = { user = it },
                    label = { Text("Admin username") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(6.dp))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it },
                    label = { Text("Admin password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(10.dp))
                Button(
                    onClick = {
                        loginError = ""
                        loading = true
                        scope.launch {
                            val cfg = AprsApi.adminConfig(user.trim(), pass)
                            loading = false
                            if (cfg != null) loggedIn = true
                            else loginError = "Login failed or server unreachable"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (loading) "Signing in..." else "Sign in") }
                if (loginError.isNotEmpty()) {
                    Text(loginError, color = Err, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            ConfigCard(user, pass)
            MotdCard(user, pass)
            MembersCard(user, pass)
            BansCard(user, pass)
            AuditCard(user, pass)
            Button(
                onClick = { loggedIn = false; pass = "" },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) { Text("Sign out") }
        }
    }
}

// ---------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------

@Composable
private fun ConfigCard(user: String, pass: String) {
    var cfg by remember { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(user) { scope.launch { cfg = AprsApi.adminConfig(user, pass) } }
    Card("Server Configuration") {
        val c = cfg
        if (c == null) {
            Text("Loading...", color = TextDim, fontSize = 12.sp)
        } else {
            c.keys().asSequence().sorted().forEach { key ->
                val raw = c.opt(key)?.toString() ?: ""
                val shown = if (key.contains("pass", true) ||
                    key.contains("key", true) ||
                    key.contains("secret", true)) "(hidden)" else raw
                KvRow(key, shown)
            }
        }
    }
}

@Composable
private fun MotdCard(user: String, pass: String) {
    var enabled by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(user) {
        scope.launch {
            val m = AprsApi.adminMotdGet(user, pass)
            if (m != null) {
                enabled = m.optBoolean("enabled", false)
                message = m.optString("message", "")
                loaded = true
            }
        }
    }

    Card("Message of the Day") {
        if (!loaded) {
            Text("Loading...", color = TextDim, fontSize = 12.sp)
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Enabled", color = TextDim, fontSize = 13.sp,
                    modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            OutlinedTextField(
                value = message, onValueChange = { message = it },
                label = { Text("Message") },
                minLines = 2, maxLines = 4,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            Button(
                onClick = {
                    saving = true
                    status = ""
                    scope.launch {
                        val ok = AprsApi.adminMotdSet(user, pass, enabled, message)
                        saving = false
                        status = if (ok) "Saved" else "Failed - try again"
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) { Text(if (saving) "Saving..." else "Save MOTD") }
            if (status.isNotEmpty()) {
                Text(status,
                    color = if (status == "Saved") Ok else Err,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun MembersCard(user: String, pass: String) {
    var list by remember { mutableStateOf<JSONArray?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(user) { scope.launch { list = AprsApi.adminMembers(user, pass) } }
    Card("Members (${list?.length() ?: "..."})") {
        val arr = list
        if (arr == null) {
            Text("Loading...", color = TextDim, fontSize = 12.sp)
        } else if (arr.length() == 0) {
            Text("No members registered.", color = TextDim, fontSize = 12.sp)
        } else {
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                MemberRow(m)
            }
        }
    }
}

@Composable
private fun MemberRow(m: JSONObject) {
    val call = m.optString("callsign", "?")
    val name = m.optString("name", "")
    val email = m.optString("email", "")
    val sessions = m.optInt("sessions", 0)
    val lastLogin = m.optLong("last_login", 0)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(call, color = Accent, fontWeight = FontWeight.Bold,
                fontSize = 13.sp, modifier = Modifier.weight(1f))
            if (sessions > 0) {
                Text("$sessions online",
                    color = Ok, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
        if (name.isNotEmpty() || email.isNotEmpty()) {
            Text(
                listOfNotNull(name.ifEmpty { null }, email.ifEmpty { null })
                    .joinToString("  -  "),
                color = TextDim, fontSize = 11.sp
            )
        }
        if (lastLogin > 0) {
            Text("Last login " + formatTime(lastLogin * 1000),
                color = TextDim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BansCard(user: String, pass: String) {
    var list by remember { mutableStateOf<JSONArray?>(null) }
    var newCall by remember { mutableStateOf("") }
    var newReason by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(refreshKey) {
        scope.launch { list = AprsApi.adminBans(user, pass) }
    }
    Card("Bans (${list?.length() ?: "..."})") {
        val arr = list
        if (arr == null) {
            Text("Loading...", color = TextDim, fontSize = 12.sp)
        } else {
            if (arr.length() == 0) {
                Text("No bans set.", color = TextDim, fontSize = 12.sp)
            } else {
                for (i in 0 until arr.length()) {
                    val b = arr.optJSONObject(i) ?: continue
                    val call = b.optString("callsign", "?")
                    val reason = b.optString("reason", "")
                    val added = b.optLong("added", 0)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(call, color = TextBase,
                                fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (reason.isNotEmpty()) {
                                Text(reason, color = TextDim, fontSize = 11.sp)
                            }
                            if (added > 0) {
                                Text("Added " + formatTime(added * 1000),
                                    color = TextDim, fontSize = 11.sp)
                            }
                        }
                        TextButton(onClick = {
                            scope.launch {
                                val ok = AprsApi.adminBanRemove(user, pass, call)
                                status = if (ok) "Removed $call" else "Failed"
                                refreshKey++
                            }
                        }) { Text("Unban", color = AccentRose, fontSize = 12.sp) }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Text("Add a ban", color = TextDim, fontSize = 12.sp)
            OutlinedTextField(
                value = newCall, onValueChange = { newCall = it },
                label = { Text("Callsign") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            )
            OutlinedTextField(
                value = newReason, onValueChange = { newReason = it },
                label = { Text("Reason") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            )
            Button(
                onClick = {
                    scope.launch {
                        val ok = AprsApi.adminBanAdd(user, pass, newCall, newReason)
                        status = if (ok) "Added ${newCall.uppercase()}" else "Failed"
                        if (ok) { newCall = ""; newReason = ""; refreshKey++ }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) { Text("Add ban") }
            if (status.isNotEmpty()) {
                Text(status,
                    color = if (status.startsWith("Failed")) Err else Ok,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun AuditCard(user: String, pass: String) {
    var list by remember { mutableStateOf<JSONArray?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(user) { scope.launch { list = AprsApi.adminAudit(user, pass, 50) } }
    Card("Audit Log (last 50)") {
        val arr = list
        if (arr == null) {
            Text("Loading...", color = TextDim, fontSize = 12.sp)
        } else if (arr.length() == 0) {
            Text("No entries.", color = TextDim, fontSize = 12.sp)
        } else {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val ts = e.optLong("timestamp", 0).let {
                        if (it > 0) it else e.optLong("time", 0)
                    }
                    val actor = e.optString("actor", e.optString("user", "?"))
                    val action = e.optString("action", "")
                    val target = e.optString("target", e.optString("callsign", ""))
                    val detail = e.optString("detail", e.optString("reason", ""))
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(action, color = Accent,
                                fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                modifier = Modifier.weight(1f))
                            if (ts > 0) {
                                Text(formatTime(ts * 1000),
                                    color = TextDim, fontSize = 11.sp)
                            }
                        }
                        val line2 = listOfNotNull(
                            actor.ifEmpty { null },
                            target.ifEmpty { null },
                            detail.ifEmpty { null }
                        ).joinToString("  -  ")
                        if (line2.isNotEmpty()) {
                            Text(line2, color = TextDim, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

@Composable
private fun KvRow(key: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(key, color = TextDim, fontSize = 12.sp,
            modifier = Modifier.weight(1f))
        Text(value, color = TextBase, fontSize = 12.sp,
            fontWeight = FontWeight.Bold)
    }
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("d MMM HH:mm", Locale.UK).format(Date(ms))

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
        Text(title.uppercase(), color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.size(8.dp))
        content()
    }
}
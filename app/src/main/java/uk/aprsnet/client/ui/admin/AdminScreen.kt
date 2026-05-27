package uk.aprsnet.client.ui.admin

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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.json.JSONObject
import uk.aprsnet.client.net.AprsApi
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.BgPanel
import uk.aprsnet.client.ui.theme.Err
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim

/**
 * Admin: authenticate with the server admin credentials, then view the live
 * server configuration. Config editing remains on the web admin panel; this
 * gives a native at-a-glance view of the server's settings.
 */
@Composable
fun AdminScreen(modifier: Modifier = Modifier) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var config by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        if (config == null) {
            Card("Admin Login") {
                Text(
                    "Sign in with the server admin credentials to view the " +
                        "live configuration.",
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
                        error = ""
                        loading = true
                        scope.launch {
                            val cfg = AprsApi.adminConfig(user.trim(), pass)
                            loading = false
                            if (cfg != null) config = cfg
                            else error = "Login failed or server unreachable"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (loading) "Signing in..." else "Sign in") }
                if (error.isNotEmpty()) {
                    Text(error, color = Err, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        } else {
            Card("Server Configuration") {
                val cfg = config!!
                cfg.keys().asSequence().sorted().forEach { key ->
                    val value = cfg.opt(key)?.toString() ?: ""
                    // never display secrets in full
                    val shown = if (key.contains("pass", true) ||
                                    key.contains("key", true) ||
                                    key.contains("secret", true)
                    ) "(hidden)" else value
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(key, color = TextDim, fontSize = 12.sp,
                            modifier = Modifier.weight(1f))
                        Text(shown, color = TextBase, fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                "Configuration changes are made on the web admin panel at " +
                    "www.aprsnet.uk.",
                color = TextDim, fontSize = 11.sp,
                modifier = Modifier.padding(8.dp)
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
        Text(title.uppercase(), color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.size(8.dp))
        content()
    }
}
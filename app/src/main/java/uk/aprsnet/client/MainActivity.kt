package uk.aprsnet.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import uk.aprsnet.client.net.AprsWebSocket
import uk.aprsnet.client.service.AprsService
import uk.aprsnet.client.service.NotificationHelper
import uk.aprsnet.client.ui.map.MapScreen
import uk.aprsnet.client.ui.messages.ConversationListScreen
import uk.aprsnet.client.ui.messages.ThreadScreen
import uk.aprsnet.client.ui.theme.AprsNetTheme
import uk.aprsnet.client.ui.theme.BgHeader
import uk.aprsnet.client.ui.theme.Err
import uk.aprsnet.client.ui.theme.Ok

/**
 * APRS Net - native Android app.
 * Stage 2: bottom-nav with Map + Messages; chat threads with ACK/green
 * bubbles; foreground service for background message notifications.
 */
class MainActivity : ComponentActivity() {

    private var pendingThread: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannels(this)
        pendingThread = intent?.getStringExtra(NotificationHelper.EXTRA_OPEN_THREAD)

        // start the background service that holds the WebSocket
        startAprsService()

        setContent {
            AprsNetTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(initialThread = pendingThread)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // a notification tap while running - handled via recreate for simplicity
        intent.getStringExtra(NotificationHelper.EXTRA_OPEN_THREAD)?.let {
            setIntent(intent)
            recreate()
        }
    }

    private fun startAprsService() {
        val svc = Intent(this, AprsService::class.java).apply {
            action = AprsService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
    }
}

private enum class Tab { MAP, MESSAGES }

@Composable
private fun AppRoot(initialThread: String?) {
    val vm: AprsViewModel = viewModel()
    val conn by vm.connState.collectAsState()
    val stations by vm.stations.collectAsState()
    val unread by vm.totalUnread.collectAsState(initial = 0)

    var tab by remember { mutableStateOf(if (initialThread != null) Tab.MESSAGES else Tab.MAP) }
    var openThread by remember { mutableStateOf(initialThread) }

    // request notification + location permissions
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.startBeaconingIfPermitted() }
    LaunchedEffect(Unit) {
        val wanted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) wanted += Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) wanted += Manifest.permission.ACCESS_FINE_LOCATION
        if (wanted.isNotEmpty()) permLauncher.launch(wanted.toTypedArray())
        vm.start("", "")   // connects; uses stored callsign if set
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            conn = conn,
            stationCount = stations.size,
            threadTitle = openThread,
            onBack = { openThread = null }
        )
        Box(modifier = Modifier.weight(1f)) {
            when {
                tab == Tab.MAP -> MapScreen(vm)
                openThread != null -> ThreadScreen(vm, openThread!!)
                else -> ConversationListScreen(vm, onOpenThread = { openThread = it })
            }
        }
        NavigationBar {
            NavigationBarItem(
                selected = tab == Tab.MAP,
                onClick = { tab = Tab.MAP },
                icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
                label = { Text("Map") }
            )
            NavigationBarItem(
                selected = tab == Tab.MESSAGES,
                onClick = { tab = Tab.MESSAGES },
                icon = {
                    BadgedBox(badge = {
                        if (unread > 0) Badge { Text(unread.toString()) }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Messages")
                    }
                },
                label = { Text("Messages") }
            )
        }
    }
}

@Composable
private fun TopBar(
    conn: AprsWebSocket.ConnState,
    stationCount: Int,
    threadTitle: String?,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgHeader)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (threadTitle != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF60A5FA)
                )
            }
            Text(threadTitle, color = Color(0xFF60A5FA))
        } else {
            Text("APRS Net", color = Color(0xFF60A5FA))
        }
        Spacer(Modifier.weight(1f))
        Text("$stationCount stn", color = Color(0xFF94A3B8))
        Spacer(Modifier.size(8.dp))
        val dot = when (conn) {
            AprsWebSocket.ConnState.AUTHED,
            AprsWebSocket.ConnState.CONNECTED -> Ok
            else -> Err
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dot)
        )
    }
}
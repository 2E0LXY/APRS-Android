package uk.aprsnet.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import uk.aprsnet.client.net.AprsWebSocket
import uk.aprsnet.client.service.AprsService
import uk.aprsnet.client.service.NotificationHelper
import uk.aprsnet.client.ui.contacts.ContactsScreen
import uk.aprsnet.client.ui.info.InfoScreen
import uk.aprsnet.client.ui.map.MapScreen
import uk.aprsnet.client.ui.messages.ConversationListScreen
import uk.aprsnet.client.ui.messages.ThreadScreen
import uk.aprsnet.client.ui.settings.SettingsScreen
import uk.aprsnet.client.ui.stations.StationsScreen
import uk.aprsnet.client.ui.status.StatusScreen
import uk.aprsnet.client.ui.utilities.UtilitiesScreen
import uk.aprsnet.client.ui.weather.WeatherScreen
import uk.aprsnet.client.ui.iss.IssScreen
import uk.aprsnet.client.ui.admin.AdminScreen
import uk.aprsnet.client.ui.theme.AprsNetTheme
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.BgDeep
import uk.aprsnet.client.ui.theme.BgHeader
import uk.aprsnet.client.ui.theme.TextDim
import uk.aprsnet.client.ui.theme.TextHi
import uk.aprsnet.client.ui.theme.Err
import uk.aprsnet.client.ui.theme.Ok

/**
 * APRS Net - native Android app.
 * Stage 5: adds Settings, Utilities and Info behind a top-bar More menu;
 * five primary tabs remain Map / Stations / Messages / Contacts / Status.
 */
class MainActivity : ComponentActivity() {

    private var pendingThread: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // On API 35+ apps render edge-to-edge by default; opt in explicitly
        // so we control insets uniformly across versions and let Compose
        // apply safeDrawingPadding to keep our UI clear of the system bars.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        NotificationHelper.ensureChannels(this)
        configureOsmdroid()
        // Pre-decode the APRS symbol sheets so first map render doesn't
        // block on bitmap factory work in the UI thread.
        uk.aprsnet.client.aprs.AprsSymbols.init(this)
        pendingThread = intent?.getStringExtra(NotificationHelper.EXTRA_OPEN_THREAD)
        // foreground service is NOT started here. On API 34+ (and stricter
        // still on Android 16/17) starting foregroundServiceType=location
        // before location permission is granted throws and kills the app.
        // The service is started from AppRoot once permissions resolve.
        setContent {
            AprsNetTheme {
                // Apply the user's chosen theme gradient at the root.
                // Use the same SharedPreferences file SettingsStore uses.
                val themePrefs = this@MainActivity.getSharedPreferences(
                    "aprs_settings", android.content.Context.MODE_PRIVATE)
                val themeId = themePrefs.getInt("theme_id", 0)
                    .coerceIn(0, uk.aprsnet.client.ui.theme.APP_THEMES.lastIndex)
                val theme = uk.aprsnet.client.ui.theme.APP_THEMES[themeId]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(theme.gradientTop, theme.gradientBottom)
                            )
                        )
                ) {
                    AppRoot(initialThread = pendingThread)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(NotificationHelper.EXTRA_OPEN_THREAD)?.let {
            setIntent(intent)
            recreate()
        }
    }

    /**
     * Configure osmdroid up-front: explicit cache path in app-private
     * storage, a sensible user-agent (the package name), and load any
     * saved preferences. Doing this in onCreate - before any map
     * composes - prevents a class of background-thread crashes that
     * otherwise tear the app down moments after the first frame.
     */
    private fun configureOsmdroid() {
        runCatching {
            val cfg = org.osmdroid.config.Configuration.getInstance()
            cfg.load(
                applicationContext,
                android.preference.PreferenceManager
                    .getDefaultSharedPreferences(applicationContext)
            )
            cfg.userAgentValue = packageName
            cfg.osmdroidBasePath = java.io.File(cacheDir, "osmdroid")
                .apply { mkdirs() }
            cfg.osmdroidTileCache = java.io.File(cacheDir, "osmdroid/tiles")
                .apply { mkdirs() }
        }
    }

    fun startAprsService() {
        val svc = Intent(this, AprsService::class.java).apply {
            action = AprsService.ACTION_START
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc)
            } else {
                startService(svc)
            }
        }
    }
}

private enum class Screen {
    MAP, STATIONS, MESSAGES, CONTACTS, STATUS,   // primary tabs
    SETTINGS, UTILITIES, WEATHER, ISS, ADMIN, INFO   // More menu
}

@Composable
private fun AppRoot(initialThread: String?) {
    val vm: AprsViewModel = viewModel()
    val conn by vm.connState.collectAsState()
    val stations by vm.stations.collectAsState()
    val unread by vm.totalUnread.collectAsState(initial = 0)

    var screen by remember {
        mutableStateOf(if (initialThread != null) Screen.MESSAGES else Screen.MAP)
    }
    var openThread by remember { mutableStateOf(initialThread) }
    // Tapping a callsign in a message thread sets this and jumps to the
    // Map tab, which centres on that station and opens its detail dialog.
    var focusCallsign by remember { mutableStateOf<String?>(null) }
    // True only for a fresh, non-notification launch: once the first GPS
    // fix arrives the map re-centres on the user (consumed thereafter).
    var centreOnUser by remember { mutableStateOf(initialThread == null) }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    // One-shot post-install setup dialog: battery exemption + pin to home.
    // Shows once on first launch; user can dismiss permanently.
    var showSetup by remember {
        mutableStateOf(!vm.settings.hasShownSetup)
    }
    var showBgLocationDisclosure by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // permissions resolved - now safe to start the foreground service;
        // it will only beacon if location permission is held.
        (ctx as? MainActivity)?.startAprsService()
        vm.startBeaconingIfPermitted()
        // After FINE_LOCATION is granted, Android 10+ requires a SEPARATE
        // prompt for BACKGROUND_LOCATION (you cannot bundle them). Fire it
        // now so beaconing continues with the screen off.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
        ) {
            // Show mandatory prominent disclosure before requesting background location
            showBgLocationDisclosure = true
        }
    }
    LaunchedEffect(Unit) {
        val wanted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) wanted += Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) wanted += Manifest.permission.ACCESS_FINE_LOCATION
        if (wanted.isNotEmpty()) {
            permLauncher.launch(wanted.toTypedArray())
        } else {
            (ctx as? MainActivity)?.startAprsService()
        }
        vm.start("", "")
    }

    // Plain icon tap (no notification extras): jump to the message centre
    // if anything is unread, otherwise leave the user on the map (which
    // will centre on their own location via centreOnUser, set above).
    LaunchedEffect(Unit) {
        if (initialThread == null) {
            val unreadNow = runCatching { vm.totalUnread.first() }.getOrDefault(0)
            if (unreadNow > 0) screen = Screen.MESSAGES
        }
    }

    fun openConversation(call: String) {
        openThread = call
        screen = Screen.MESSAGES
    }

    val isPrimaryTab = screen in listOf(
        Screen.MAP, Screen.STATIONS, Screen.MESSAGES, Screen.CONTACTS, Screen.STATUS
    )

    val theme = uk.aprsnet.client.ui.theme.APP_THEMES.getOrNull(vm.settings.themeId)
        ?: uk.aprsnet.client.ui.theme.APP_THEMES[0]
    val bgBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(theme.gradientTop, theme.gradientBottom)
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .safeDrawingPadding()
    ) {
        if (!isPrimaryTab || (screen == Screen.MESSAGES && openThread != null)) {
            SimpleBackBar(
                title = screenTitle(screen, openThread),
                onBack = {
                    if (screen == Screen.MESSAGES && openThread != null) openThread = null
                    else screen = Screen.MAP
                },
                onTitleClick = if (screen == Screen.MESSAGES && openThread != null) {
                    {
                        focusCallsign = openThread
                        openThread = null
                        screen = Screen.MAP
                    }
                } else null
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            // Always keep MapScreen composed so the osmdroid AndroidView stays
            // in the View hierarchy. Prevents OSM tile layer floating above
            // Compose content after recomposition (AndroidView z-order bug).
            MapScreen(
                vm,
                onSendMessage = { openConversation(it) },
                active = screen == Screen.MAP,
                centreOnUserLocation = centreOnUser,
                onCentredOnUser = { centreOnUser = false },
                focusCallsign = focusCallsign,
                onFocusConsumed = { focusCallsign = null }
            )
            if (screen != Screen.MAP) {
                Box(Modifier.fillMaxSize().background(BgDeep)) {
                    when (screen) {
                        Screen.STATIONS -> StationsScreen(vm, onSendMessage = { openConversation(it) })
                        Screen.MESSAGES ->
                            if (openThread != null) ThreadScreen(vm, openThread!!)
                            else ConversationListScreen(vm, onOpenThread = { openThread = it })
                        Screen.CONTACTS -> ContactsScreen(vm, onMessage = { openConversation(it) })
                        Screen.STATUS -> SettingsScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                        Screen.UTILITIES -> UtilitiesScreen()
                        Screen.WEATHER -> WeatherScreen(vm)
                        Screen.ISS -> IssScreen()
                        Screen.ADMIN -> AdminScreen()
                        Screen.INFO -> InfoScreen()
                        else -> {}
                    }
                }
            }
        }
        if (isPrimaryTab) {
            NavigationBar {
                NavItem(screen == Screen.MAP, { screen = Screen.MAP }, Icons.Default.Map, "Map")
                NavItem(screen == Screen.STATIONS, { screen = Screen.STATIONS }, Icons.Default.Place, "Stations")
                NavigationBarItem(
                    selected = screen == Screen.MESSAGES,
                    onClick = { screen = Screen.MESSAGES },
                    icon = {
                        BadgedBox(badge = {
                            if (unread > 0) Badge { Text(unread.toString()) }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Messages")
                        }
                    },
                    label = { Text("Messages", maxLines = 1, fontSize = 10.sp) }
                )
                NavItem(screen == Screen.CONTACTS, { screen = Screen.CONTACTS }, Icons.Default.Contacts, "Contacts")
                NavItem(screen == Screen.STATUS, { screen = Screen.STATUS }, Icons.Default.Settings, "Settings")
            }
        }

        // One-shot post-install setup dialog. Explains the two things Android
        // hides behind extra friction: Doze battery exemption (so the FGS and
        // WebSocket survive screen-off) and home-screen pin (Android 8+ no
        // longer auto-pins apps on install).
        if (showSetup) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    vm.settings.hasShownSetup = true
                    showSetup = false
                },
                title = { Text("Background reliability") },
                text = {
                    Column {
                        Text(
                            "Android puts apps into Doze when the screen is off, " +
                                "which drops the APRS connection and stops you receiving " +
                                "messages. Two one-tap settings keep APRS Net running:",
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.size(10.dp))
                        Text("\u2022  Battery exemption \u2013 keeps the connection alive when screen is off.", fontSize = 13.sp)
                        Spacer(Modifier.size(4.dp))
                        Text("\u2022  Add to home screen \u2013 puts an icon on your launcher.", fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = {
                            (ctx as? MainActivity)?.let {
                                uk.aprsnet.client.util.SetupHelper.requestBatteryUnrestricted(it)
                            }
                        }) { Text("Allow battery exemption") }
                        TextButton(onClick = {
                            uk.aprsnet.client.util.SetupHelper.requestPinHomeShortcut(ctx)
                        }) { Text("Add to home screen") }
                        TextButton(onClick = {
                            vm.settings.hasShownSetup = true
                            showSetup = false
                        }) { Text("Done") }
                    }
                }
            )
        }

        // ── Mandatory prominent disclosure — ACCESS_BACKGROUND_LOCATION ──────
        // Google Play policy requires this full-interstitial disclosure to be
        // shown BEFORE the system permission prompt for background location.
        // It must name the feature, the data collected, and why it is needed.
        if (showBgLocationDisclosure) {
            AlertDialog(
                onDismissRequest = { showBgLocationDisclosure = false },
                title = {
                    Text(
                        "Background location — APRS SmartBeacon",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            "APRS Net accesses your precise location in the background to run SmartBeacon.",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "SmartBeacon automatically transmits your GPS position to the APRS-IS " +
                            "amateur radio network at adaptive intervals — more often when moving, " +
                            "less often when stationary. This continues while the app is in the " +
                            "background so your track remains visible to other licensed amateur " +
                            "radio operators on aprs.fi and APRS clients worldwide.",
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Location data is sent as standard APRS position packets over the " +
                            "public APRS-IS network. No location data is stored on APRS Net servers. " +
                            "Background location is only used when SmartBeacon or Fixed beaconing " +
                            "is enabled in Settings.",
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "On the next screen, tap \"Allow all the time\" to enable background beaconing.",
                            fontSize = 13.sp,
                            color = Accent
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showBgLocationDisclosure = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            (ctx as? MainActivity)?.requestPermissions(
                                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 4242
                            )
                        }
                    }) { Text("Continue") }
                },
                dismissButton = {
                    TextButton(onClick = { showBgLocationDisclosure = false }) {
                        Text("Not now")
                    }
                }
            )
        }
    }
}

private fun screenTitle(screen: Screen, thread: String?): String = when (screen) {
    Screen.MESSAGES -> thread ?: "APRS Net"
    Screen.SETTINGS -> "Settings"
    Screen.UTILITIES -> "Utilities"
    Screen.WEATHER -> "Weather"
    Screen.ISS -> "ISS Tracker"
    Screen.ADMIN -> "Admin"
    Screen.INFO -> "About"
    else -> "APRS Net"
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, maxLines = 1, fontSize = 10.sp) }
    )
}

@Composable
private fun SimpleBackBar(title: String, onBack: () -> Unit, onTitleClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgHeader)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Accent
            )
        }
        Text(
            title,
            color = TextHi,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            textDecoration = if (onTitleClick != null)
                androidx.compose.ui.text.style.TextDecoration.Underline
            else androidx.compose.ui.text.style.TextDecoration.None,
            modifier = if (onTitleClick != null)
                Modifier.clickable(onClick = onTitleClick)
            else Modifier
        )
    }
}
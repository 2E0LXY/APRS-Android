package uk.aprsnet.client.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uk.aprsnet.client.aprs.PacketParser
import uk.aprsnet.client.data.AppDatabase
import uk.aprsnet.client.data.MessageRepository
import uk.aprsnet.client.data.SettingsStore
import uk.aprsnet.client.location.BeaconManager
import uk.aprsnet.client.location.LocationProvider
import uk.aprsnet.client.net.AprsWebSocket

/**
 * Foreground service: keeps the APRS WebSocket connected while the app is
 * backgrounded, so messages arrive like texts AND smart-beaconing continues
 * even when the app is closed.
 *
 * Holds its own WebSocket, repository, and beacon manager.
 */
class AprsService : Service() {

    companion object {
        const val ACTION_START = "uk.aprsnet.client.START"
        const val ACTION_SEND = "uk.aprsnet.client.SEND"
        const val EXTRA_TO = "to"
        const val EXTRA_TEXT = "text"
        const val EXTRA_CALL = "callsign"
        const val EXTRA_PASS = "passcode"
        private const val FG_ID = 4201
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ws = AprsWebSocket()
    private lateinit var repo: MessageRepository
    private lateinit var settings: SettingsStore
    private lateinit var beacon: BeaconManager
    private var started = false
    private var quietHours = false

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        repo = MessageRepository(AppDatabase.get(this).messageDao(), ws)
        settings = SettingsStore(this)
        beacon = BeaconManager(LocationProvider(this), ws, settings)

        // incoming messages -> notifications
        scope.launch {
            ws.rawPackets.collect { raw ->
                val parsed = PacketParser.parse(raw)
                if (parsed is PacketParser.Parsed.Msg) {
                    val incoming = repo.handleIncoming(raw)
                    if (incoming != null && settings.notifyMessages) {
                        NotificationHelper.showMessage(
                            this@AprsService, incoming, settings.inQuietHours()
                        )
                    }
                }
            }
        }
        // retry sweep
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                runCatching { repo.retrySweep() }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SEND -> {
                val to = intent.getStringExtra(EXTRA_TO)
                val text = intent.getStringExtra(EXTRA_TEXT)
                if (to != null && text != null) {
                    scope.launch { runCatching { repo.sendMessage(to, text) } }
                }
            }
            else -> {
                val call = settings.callsign
                val pass = settings.passcode
                repo.myCallsign = call
                if (!started) {
                    startForeground(FG_ID, NotificationHelper.serviceNotification(this))
                    if (call.isNotEmpty()) ws.setCredentials(call, pass)
                    ws.connect()
                    startBackgroundBeaconing()
                    started = true
                } else if (call.isNotEmpty()) {
                    ws.setCredentials(call, pass)
                }
            }
        }
        return START_STICKY
    }

    /** Continue smart-beaconing in the background if permitted and enabled. */
    private fun startBackgroundBeaconing() {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fine && settings.positionMode != "off") {
            beacon.start(scope)
        }
    }

    override fun onDestroy() {
        beacon.stop()
        ws.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
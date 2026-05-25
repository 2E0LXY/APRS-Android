package uk.aprsnet.client.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import uk.aprsnet.client.aprs.PacketParser
import uk.aprsnet.client.data.AppDatabase
import uk.aprsnet.client.data.MessageRepository
import uk.aprsnet.client.net.AprsWebSocket

/**
 * Foreground service: keeps the APRS WebSocket connected while the app is
 * backgrounded, so message notifications arrive like text messages.
 *
 * Holds its own WebSocket + repository. Raises a MessagingStyle notification
 * for each incoming message addressed to the user, and handles inline replies
 * forwarded from ReplyReceiver.
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
    private var started = false
    private var quietHours = false

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
        repo = MessageRepository(AppDatabase.get(this).messageDao(), ws)

        // incoming messages -> notifications
        scope.launch {
            ws.rawPackets.collect { raw ->
                val parsed = PacketParser.parse(raw)
                if (parsed is PacketParser.Parsed.Msg) {
                    val incoming = repo.handleIncoming(raw)
                    if (incoming != null) {
                        NotificationHelper.showMessage(
                            this@AprsService, incoming, quietHours
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
                val call = intent?.getStringExtra(EXTRA_CALL) ?: ""
                val pass = intent?.getStringExtra(EXTRA_PASS) ?: ""
                repo.myCallsign = call.trim().uppercase()
                if (!started) {
                    startForeground(FG_ID, NotificationHelper.serviceNotification(this))
                    if (call.isNotEmpty()) ws.setCredentials(call, pass)
                    ws.connect()
                    started = true
                } else if (call.isNotEmpty()) {
                    ws.setCredentials(call, pass)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        ws.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
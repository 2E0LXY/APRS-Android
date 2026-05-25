package uk.aprsnet.client.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives an inline reply typed into an APRS message notification and
 * forwards it to the running AprsService to transmit.
 */
class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationHelper.KEY_REPLY)?.toString() ?: return
        val to = intent.getStringExtra(NotificationHelper.EXTRA_OPEN_THREAD) ?: return
        if (reply.isBlank()) return

        // Hand off to the service, which owns the WebSocket + repository
        val svc = Intent(context, AprsService::class.java).apply {
            action = AprsService.ACTION_SEND
            putExtra(AprsService.EXTRA_TO, to)
            putExtra(AprsService.EXTRA_TEXT, reply.toString())
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
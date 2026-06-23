package uk.aprsnet.client.auto

import android.os.Build
import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.InputCallback
import androidx.car.app.model.InputTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.OnClickListener
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import uk.aprsnet.client.data.AppDatabase
import uk.aprsnet.client.data.MessageEntity
import uk.aprsnet.client.service.AprsService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows the last 10 messages for [remoteCall] and provides a parked-only
 * Reply button that pushes an InputTemplate for text/voice reply.
 *
 * Reply is dispatched to AprsService via ACTION_SEND — same path as
 * the notification inline-reply ReplyReceiver — so no new IPC needed.
 */
class MessageThreadCarScreen(
    carContext: CarContext,
    private val db: AppDatabase,
    private val remoteCall: String
) : Screen(carContext) {

    private var messages: List<MessageEntity> = emptyList()
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.UK)

    init {
        lifecycleScope.launch {
            // Mark read as soon as the thread is opened on Auto
            db.messageDao().markRead(remoteCall)
        }
        lifecycleScope.launch {
            db.messageDao().thread(remoteCall).collect { list ->
                messages = list
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        // Render last 10 messages as a scrollable text block.
        // MessageTemplate (Car App Library) is distraction-optimised.
        val body = messages.takeLast(10).joinToString("\n") { msg ->
            val dir  = if (msg.outgoing) "▶" else "◀"
            val time = timeFmt.format(Date(msg.timestamp))
            "$dir [$time] ${msg.text}"
        }.ifEmpty { "No messages yet" }

        val replyAction = Action.Builder()
            .setTitle("Reply")
            .setBackgroundColor(CarColor.BLUE)
            .setOnClickListener(
                // InputTemplate requires car to be parked — enforced by the platform
                ParkedOnlyOnClickListener.create { screenManager.push(replyScreen()) }
            )
            .build()

        return MessageTemplate.Builder(body)
            .setTitle(remoteCall)
            .setHeaderAction(Action.BACK)
            .addAction(replyAction)
            .build()
    }

    /** Pushes an InputTemplate so the driver can dictate/type a reply. */
    private fun replyScreen(): Screen = object : Screen(carContext) {
        override fun onGetTemplate(): Template =
            InputTemplate.Builder()
                .setTitle("Reply to $remoteCall")
                .setHeaderAction(Action.BACK)
                .setHint("Dictate message")
                .setInputCallback(object : InputCallback {
                    override fun onInputSubmitted(text: String) {
                        if (text.isNotBlank()) sendMessage(text.trim())
                        screenManager.pop()
                    }
                    override fun onInputTextChanged(text: String) { /* live preview unused */ }
                })
                .build()
    }

    private fun sendMessage(text: String) {
        val svc = Intent(carContext, AprsService::class.java).apply {
            action  = AprsService.ACTION_SEND
            putExtra(AprsService.EXTRA_TO,   remoteCall)
            putExtra(AprsService.EXTRA_TEXT, text)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            carContext.startForegroundService(svc)
        } else {
            carContext.startService(svc)
        }
    }
}

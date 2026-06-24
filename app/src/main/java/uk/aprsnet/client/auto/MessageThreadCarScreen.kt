package uk.aprsnet.client.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import uk.aprsnet.client.data.AppDatabase
import uk.aprsnet.client.data.MessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shows the last 10 messages for [remoteCall].
 *
 * Reply is handled via the notification inline-reply action (RemoteInput /
 * MessagingStyle) which Android Auto surfaces automatically — no separate
 * InputTemplate screen is needed here.
 */
class MessageThreadCarScreen(
    carContext: CarContext,
    private val db: AppDatabase,
    private val remoteCall: String
) : Screen(carContext) {

    private var messages: List<MessageEntity> = emptyList()
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.UK)

    init {
        lifecycleScope.launch { db.messageDao().markRead(remoteCall) }
        lifecycleScope.launch {
            db.messageDao().thread(remoteCall).collect { list ->
                messages = list
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val body = messages.takeLast(10).joinToString("\n") { msg ->
            val dir  = if (msg.outgoing) "▶" else "◀"
            val time = timeFmt.format(Date(msg.timestamp))
            "$dir [$time] ${msg.text}"
        }.ifEmpty { "No messages yet" }

        // Reply via the notification MessagingStyle inline-reply action
        // (Android Auto surfaces it automatically on new message notifications).
        val replyHintAction = Action.Builder()
            .setTitle("Reply via notification")
            .setBackgroundColor(CarColor.BLUE)
            .setOnClickListener(
                ParkedOnlyOnClickListener.create {
                    CarToast.makeText(
                        carContext,
                        "Use the notification to reply to $remoteCall",
                        CarToast.LENGTH_LONG
                    ).show()
                }
            )
            .build()

        return MessageTemplate.Builder(body)
            .setTitle(remoteCall)
            .setHeaderAction(Action.BACK)
            .addAction(replyHintAction)
            .build()
    }
}

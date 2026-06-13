package uk.aprsnet.client.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import uk.aprsnet.client.MainActivity
import uk.aprsnet.client.R
import uk.aprsnet.client.data.MessageEntity

/**
 * Builds SMS-style (MessagingStyle) notifications for incoming APRS messages.
 * Tapping opens the conversation; an inline Reply action is attached.
 */
object NotificationHelper {

    const val CHANNEL_MESSAGES = "aprs_messages"
    const val CHANNEL_SERVICE = "aprs_service"
    const val KEY_REPLY = "key_reply"
    const val EXTRA_OPEN_THREAD = "open_thread"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MESSAGES, "APRS Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Incoming APRS text messages" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE, "Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "APRS Net background connection"
                // The foreground-service notification is permanently "ongoing"
                // while connected, so a default showBadge=true on this channel
                // left a launcher notification dot showing 24/7 even with zero
                // unread messages. Only CHANNEL_MESSAGES should drive the badge.
                setShowBadge(false)
            }
        )
    }

    /**
     * Cancel the chat-style notification for [callsign], if one is showing.
     * Called when a thread is marked read in-app (not via the notification's
     * own auto-cancel), so the launcher badge dot clears immediately.
     */
    fun clearMessage(ctx: Context, callsign: String) {
        runCatching {
            NotificationManagerCompat.from(ctx).cancel(callsign.hashCode())
        }
    }

    /** The persistent low-priority notification the foreground service runs under. */
    fun serviceNotification(ctx: Context): android.app.Notification {
        return NotificationCompat.Builder(ctx, CHANNEL_SERVICE)
            .setContentTitle("APRS Net")
            .setContentText("Connected - listening for messages")
            // ic_notification is a monochrome white vector; required by Android
            // for the status bar. Using ic_launcher_foreground (multi-colour
            // adaptive icon) here used to render as an invisible / blank
            // square on Android 8+ and especially Samsung One UI, which is
            // why the persistent icon seemed to be missing.
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent(ctx, null))
            .build()
    }

    /** Post a chat-style notification for an incoming message. */
    fun showMessage(ctx: Context, msg: MessageEntity, quietHours: Boolean) {
        val them = Person.Builder().setName(msg.remoteCall).build()
        val me = Person.Builder().setName("Me").build()

        val style = NotificationCompat.MessagingStyle(me)
            .setConversationTitle(msg.remoteCall)
            .addMessage(msg.text, msg.timestamp, them)

        val replyInput = RemoteInput.Builder(KEY_REPLY)
            .setLabel("Reply to ${msg.remoteCall}")
            .build()

        val replyIntent = Intent(ctx, ReplyReceiver::class.java).apply {
            putExtra(EXTRA_OPEN_THREAD, msg.remoteCall)
        }
        val replyPending = PendingIntent.getBroadcast(
            ctx, msg.remoteCall.hashCode(), replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground, "Reply", replyPending
        ).addRemoteInput(replyInput).build()

        val builder = NotificationCompat.Builder(ctx, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setContentIntent(openAppIntent(ctx, msg.remoteCall))
            .setAutoCancel(true)
            .addAction(replyAction)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        if (quietHours) {
            builder.setSilent(true)
        } else {
            builder.priority = NotificationCompat.PRIORITY_HIGH
        }

        runCatching {
            NotificationManagerCompat.from(ctx)
                .notify(msg.remoteCall.hashCode(), builder.build())
        }
    }

    private fun openAppIntent(ctx: Context, thread: String?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            if (thread != null) putExtra(EXTRA_OPEN_THREAD, thread)
        }
        return PendingIntent.getActivity(
            ctx, (thread ?: "app").hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
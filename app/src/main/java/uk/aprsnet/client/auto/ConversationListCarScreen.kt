package uk.aprsnet.client.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import uk.aprsnet.client.data.AppDatabase
import uk.aprsnet.client.data.ConversationSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Root Auto screen: lists all APRS conversations, newest first.
 * Tapping a row pushes MessageThreadCarScreen for that callsign.
 * Observes the Room Flow so the list refreshes when new messages arrive.
 */
class ConversationListCarScreen(
    carContext: CarContext,
    private val db: AppDatabase
) : Screen(carContext) {

    private var conversations: List<ConversationSummary> = emptyList()
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.UK)

    init {
        lifecycleScope.launch {
            db.messageDao().conversations().collect { list ->
                conversations = list
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        // Car App Library caps list items at 6 for driver distraction; take 6 most recent.
        val items = conversations.take(6)

        if (items.isEmpty()) {
            listBuilder.setNoItemsMessage("No APRS conversations")
        } else {
            items.forEach { conv ->
                val unreadSuffix = if (conv.unread > 0) "  [${conv.unread}]" else ""
                val time = timeFmt.format(Date(conv.lastTimestamp))
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("${conv.remoteCall}$unreadSuffix")
                        .addText("$time  ${conv.lastText.take(60)}")
                        .setOnClickListener {
                            screenManager.push(
                                MessageThreadCarScreen(carContext, db, conv.remoteCall)
                            )
                        }
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setTitle("APRS Messages")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }
}

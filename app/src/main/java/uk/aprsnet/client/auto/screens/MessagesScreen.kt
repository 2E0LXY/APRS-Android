package uk.aprsnet.client.auto.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import uk.aprsnet.client.auto.AutoDataBridge

class MessagesScreen(carContext: CarContext) : Screen(carContext) {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                AutoDataBridge.messages.observe(this@MessagesScreen) { invalidate() }
            }
        })
    }

    override fun onGetTemplate(): Template {
        val myCall = AutoDataBridge.myCallsign
        val messages = AutoDataBridge.messages.value ?: emptyList()

        // Group into conversations, most-recent first
        val conversations = messages
            .groupBy { if (it.from == myCall) it.to else it.from }
            .entries
            .sortedByDescending { entry -> entry.value.maxOf { it.timestampMs } }
            .take(6)

        val itemList = ItemList.Builder()

        if (conversations.isEmpty()) {
            itemList.setNoItemsMessage("No messages")
        }

        conversations.forEach { (callsign, msgs) ->
            val latest      = msgs.maxByOrNull { it.timestampMs }
            val unread      = msgs.count { !it.acked && it.from != myCall }
            val ageStr      = latest?.let { ageString(it.timestampMs) } ?: ""

            itemList.addItem(
                Row.Builder()
                    .setTitle("$callsign${if (unread > 0) "  ($unread)" else ""}")
                    .addText(latest?.body?.take(60) ?: "")
                    .addText(ageStr)
                    .setOnClickListener {
                        screenManager.push(MessageThreadScreen(carContext, callsign))
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("Messages")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemList.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("New")
                            .setOnClickListener {
                                screenManager.push(SearchScreen(carContext, SearchScreen.Mode.COMPOSE))
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun ageString(ms: Long): String {
        val ageMin = ((System.currentTimeMillis() - ms) / 60_000L).toInt()
        return when {
            ageMin < 1    -> "now"
            ageMin < 60   -> "${ageMin}m"
            ageMin < 1440 -> "${ageMin / 60}h"
            else          -> "${ageMin / 1440}d"
        }
    }
}

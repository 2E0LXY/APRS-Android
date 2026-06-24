package uk.aprsnet.client.auto.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import uk.aprsnet.client.auto.AutoDataBridge
import java.text.SimpleDateFormat
import java.util.*

class MessageThreadScreen(
    carContext: CarContext,
    private val remoteCallsign: String
) : Screen(carContext) {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                AutoDataBridge.messages.observe(this@MessageThreadScreen) { invalidate() }
            }
        })
    }

    override fun onGetTemplate(): Template {
        val myCall = AutoDataBridge.myCallsign
        val fmt    = SimpleDateFormat("HH:mm", Locale.UK)

        val thread = (AutoDataBridge.messages.value ?: emptyList())
            .filter { it.from == remoteCallsign || it.to == remoteCallsign }
            .sortedByDescending { it.timestampMs }
            .take(6)

        val itemList = ItemList.Builder()

        if (thread.isEmpty()) {
            itemList.setNoItemsMessage("No messages with $remoteCallsign")
        }

        thread.forEach { msg ->
            val outbound = msg.from == myCall
            val dir      = if (outbound) "→" else "←"
            val ack      = if (msg.acked) " ✓" else ""
            val time     = fmt.format(Date(msg.timestampMs))

            itemList.addItem(
                Row.Builder()
                    .setTitle("$dir $time$ack")
                    .addText(msg.body)
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("$remoteCallsign ↔ $myCall")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemList.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Reply")
                            .setOnClickListener {
                                screenManager.push(MessageComposeScreen(carContext, remoteCallsign))
                            }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setTitle("Station")
                            .setOnClickListener {
                                val stn = AutoDataBridge.stations.value
                                    ?.firstOrNull { it.callsign == remoteCallsign }
                                if (stn != null) screenManager.push(StationDetailScreen(carContext, stn))
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

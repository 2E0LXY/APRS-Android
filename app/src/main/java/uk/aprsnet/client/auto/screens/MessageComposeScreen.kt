package uk.aprsnet.client.auto.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import uk.aprsnet.client.auto.AutoDataBridge

class MessageComposeScreen(
    carContext: CarContext,
    private val toCallsign: String
) : Screen(carContext) {

    // APRS-standard and common quick replies
    private val quickReplies = listOf(
        "73",
        "QRZ?",
        "QSL — received, thanks",
        "Confirmed, on my way",
        "Standing by on this freq",
        "Please call me when clear"
    )

    override fun onGetTemplate(): Template {
        val itemList = ItemList.Builder()

        quickReplies.forEach { reply ->
            itemList.addItem(
                Row.Builder()
                    .setTitle(reply)
                    .setOnClickListener {
                        AutoDataBridge.onSendMessage?.invoke(toCallsign, reply)
                        screenManager.pop()     // back to thread / messages list
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("Message → $toCallsign")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemList.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Type…")
                            .setOnClickListener {
                                screenManager.push(MessageInputScreen(carContext, toCallsign))
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}

package uk.aprsnet.client.auto.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import uk.aprsnet.client.auto.AutoDataBridge
import java.text.SimpleDateFormat
import java.util.*

class AlertsScreen(carContext: CarContext) : Screen(carContext) {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                AutoDataBridge.alerts.observe(this@AlertsScreen) { invalidate() }
            }
        })
    }

    override fun onGetTemplate(): Template {
        val alerts = (AutoDataBridge.alerts.value ?: emptyList())
            .sortedByDescending { it.timestampMs }
            .take(6)

        val fmt = SimpleDateFormat("HH:mm dd/MM", Locale.UK)
        val itemList = ItemList.Builder()

        if (alerts.isEmpty()) {
            itemList.setNoItemsMessage("No active alerts or bulletins")
        }

        alerts.forEach { alert ->
            itemList.addItem(
                Row.Builder()
                    .setTitle(alert.title)
                    .addText(alert.body.take(80))
                    .addText(fmt.format(Date(alert.timestampMs)))
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("APRS Alerts & Bulletins")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemList.build())
            .build()
    }
}

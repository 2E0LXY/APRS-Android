package uk.aprsnet.client.auto.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import uk.aprsnet.client.auto.AutoDataBridge
import java.text.SimpleDateFormat
import java.util.*

class BeaconScreen(carContext: CarContext) : Screen(carContext) {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                AutoDataBridge.myPosition.observe(this@BeaconScreen) { invalidate() }
                AutoDataBridge.connectionStatus.observe(this@BeaconScreen) { invalidate() }
                AutoDataBridge.isBeaconing.observe(this@BeaconScreen) { invalidate() }
                AutoDataBridge.lastBeaconMs.observe(this@BeaconScreen) { invalidate() }
            }
        })
    }

    override fun onGetTemplate(): Template {
        val pos       = AutoDataBridge.myPosition.value
        val status    = AutoDataBridge.connectionStatus.value ?: "Disconnected"
        val beaconing = AutoDataBridge.isBeaconing.value ?: false
        val lastMs    = AutoDataBridge.lastBeaconMs.value ?: 0L
        val fmt       = SimpleDateFormat("HH:mm:ss", Locale.UK)

        val lastBeaconStr = if (lastMs > 0) fmt.format(Date(lastMs)) else "Never"
        val ageStr = if (lastMs > 0) {
            val s = (System.currentTimeMillis() - lastMs) / 1000L
            when {
                s < 60   -> "${s}s ago"
                s < 3600 -> "${s / 60}m ago"
                else     -> "${s / 3600}h ago"
            }
        } else ""

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Callsign")
                    .addText(AutoDataBridge.myCallsign.ifBlank { "Not configured" })
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Server")
                    .addText(status)
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Position")
                    .addText(
                        if (pos != null)
                            "${"%.5f".format(pos.lat)}°N  ${"%.5f".format(pos.lon)}°E" +
                            if (pos.speed > 0.5f) "  ${pos.speed.toInt()}kt ${pos.course}°" else ""
                        else "No GPS fix"
                    )
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Last beacon")
                    .addText("$lastBeaconStr  $ageStr")
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(if (beaconing) "Beaconing…" else "Beacon Now")
                    .setBackgroundColor(if (beaconing) CarColor.YELLOW else CarColor.GREEN)
                    .setOnClickListener {
                        if (!beaconing) {
                            AutoDataBridge.onBeaconNow?.invoke()
                        }
                    }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("My Beacon")
            .setHeaderAction(Action.BACK)
            .build()
    }
}

package uk.aprsnet.client.auto.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import uk.aprsnet.client.auto.AutoDataBridge
import java.text.SimpleDateFormat
import java.util.*

class StationDetailScreen(
    carContext: CarContext,
    private val station: AutoDataBridge.StationData
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val fmt     = SimpleDateFormat("HH:mm dd/MM", Locale.UK)
        val ageMin  = ((System.currentTimeMillis() - station.lastHeardMs) / 60_000L).toInt()
        val ageStr  = when {
            ageMin < 60   -> "${ageMin}m ago"
            ageMin < 1440 -> "${ageMin / 60}h ago"
            else          -> "${ageMin / 1440}d ago"
        }

        val pane = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Position")
                    .addText("${"%.5f".format(station.lat)}°N  ${"%.5f".format(station.lon)}°E")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Last heard")
                    .addText("${fmt.format(Date(station.lastHeardMs))} ($ageStr)")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Comment / Status")
                    .addText(station.comment.ifBlank { "—" })
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Speed / Course / Alt")
                    .addText("${station.speed.toInt()}kt  ${station.course}°  ${station.altitude.toInt()}m")
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Message")
                    .setBackgroundColor(CarColor.BLUE)
                    .setOnClickListener {
                        screenManager.push(MessageComposeScreen(carContext, station.callsign))
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Show on Map")
                    .setOnClickListener {
                        screenManager.pop()         // back to StationsScreen
                        screenManager.pop()         // back to HomeScreen (or MapScreen if entered from there)
                        screenManager.push(MapScreen(carContext).also {
                            // Centre map on this station
                            AutoDataBridge.myPosition.observeForever { }  // ensure bridge is live
                        })
                    }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle("${station.callsign}  •  ${station.symbol}")
            .setHeaderAction(Action.BACK)
            .build()
    }
}

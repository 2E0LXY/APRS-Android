package uk.aprsnet.client.auto.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import uk.aprsnet.client.auto.AutoDataBridge
import java.text.SimpleDateFormat
import java.util.*

class WeatherScreen(carContext: CarContext) : Screen(carContext) {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                AutoDataBridge.weather.observe(this@WeatherScreen) { invalidate() }
            }
        })
    }

    override fun onGetTemplate(): Template {
        val wxPackets = (AutoDataBridge.weather.value ?: emptyList())
            .sortedByDescending { it.timestampMs }
            .take(6)

        val fmt = SimpleDateFormat("HH:mm", Locale.UK)

        val itemList = ItemList.Builder()

        if (wxPackets.isEmpty()) {
            itemList.setNoItemsMessage("No weather packets received")
        }

        wxPackets.forEach { wx ->
            val wind = buildString {
                wx.windSpeedKts?.let { append("${"%.0f".format(it)}kt ") }
                wx.windDirDeg?.let { append("${windDir(it)} ") }
            }.trim()

            val line1 = buildString {
                wx.tempC?.let { append("${"%.1f".format(it)}°C  ") }
                if (wind.isNotBlank()) append("$wind  ")
                wx.humidity?.let { append("RH ${it}%") }
            }.trim().ifBlank { "No data" }

            val line2 = buildString {
                wx.pressureMb?.let { append("${it.toInt()} mb  ") }
                wx.rainMmLastHour?.takeIf { it > 0 }?.let { append("Rain: ${"%.1f".format(it)}mm/h") }
            }.trim()

            val time = fmt.format(Date(wx.timestampMs))

            itemList.addItem(
                Row.Builder()
                    .setTitle("${wx.callsign}  •  $time")
                    .addText(line1)
                    .apply { if (line2.isNotBlank()) addText(line2) }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("APRS Weather")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemList.build())
            .build()
    }

    /** Convert degrees to 8-point compass label */
    private fun windDir(deg: Int): String {
        val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        return dirs[((deg + 22) / 45) % 8]
    }
}

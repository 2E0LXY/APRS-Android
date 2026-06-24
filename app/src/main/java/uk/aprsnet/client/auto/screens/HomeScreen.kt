package uk.aprsnet.client.auto.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import uk.aprsnet.client.auto.AutoDataBridge

class HomeScreen(carContext: CarContext) : Screen(carContext) {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                AutoDataBridge.connectionStatus.observe(this@HomeScreen) { invalidate() }
                AutoDataBridge.messages.observe(this@HomeScreen) { invalidate() }
                AutoDataBridge.alerts.observe(this@HomeScreen) { invalidate() }
            }
        })
    }

    override fun onGetTemplate(): Template {
        val unreadMsgs   = AutoDataBridge.messages.value?.count { !it.acked && it.from != AutoDataBridge.myCallsign } ?: 0
        val alertCount   = AutoDataBridge.alerts.value?.size ?: 0
        val stationCount = AutoDataBridge.stations.value?.size ?: 0

        val items = ItemList.Builder()
            .addItem(gridItem(0xFF1E88E5.toInt(), "M",  "Map\n$stationCount stations")  { screenManager.push(MapScreen(carContext)) })
            .addItem(gridItem(0xFF00ACC1.toInt(), "S",  "Stations")                      { screenManager.push(StationsScreen(carContext)) })
            .addItem(gridItem(0xFF43A047.toInt(), "Msg","Messages${if (unreadMsgs > 0) "\n$unreadMsgs unread" else ""}") { screenManager.push(MessagesScreen(carContext)) })
            .addItem(gridItem(0xFFFB8C00.toInt(), "Wx", "Weather")                       { screenManager.push(WeatherScreen(carContext)) })
            .addItem(gridItem(0xFFE53935.toInt(), "!",  "Alerts${if (alertCount > 0) "\n$alertCount active" else ""}") { screenManager.push(AlertsScreen(carContext)) })
            .addItem(gridItem(0xFF8E24AA.toInt(), "Bcn","Beacon")                        { screenManager.push(BeaconScreen(carContext)) })
            .build()

        return GridTemplate.Builder()
            .setTitle("APRS Net — ${AutoDataBridge.myCallsign.ifBlank { "Not configured" }}")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Search")
                            .setOnClickListener { screenManager.push(SearchScreen(carContext, SearchScreen.Mode.STATION)) }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    /** Creates a solid-circle icon with a letter label — no drawable resource required. */
    private fun gridItem(colour: Int, letter: String, title: String, onClick: () -> Unit): GridItem {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawCircle(size / 2f, size / 2f, size / 2f, Paint().apply { color = colour; isAntiAlias = true })
        c.drawText(
            letter,
            size / 2f, size / 2f + 12f,
            Paint().apply {
                color = Color.WHITE
                textSize = if (letter.length > 1) 26f else 36f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }
        )

        return GridItem.Builder()
            .setTitle(title)
            .setImage(CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build())
            .setOnClickListener(onClick)
            .build()
    }
}

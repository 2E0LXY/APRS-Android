package uk.aprsnet.client.auto.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import uk.aprsnet.client.auto.AutoDataBridge
import kotlin.math.*

class StationsScreen(carContext: CarContext) : Screen(carContext) {

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                AutoDataBridge.stations.observe(this@StationsScreen) { invalidate() }
                AutoDataBridge.myPosition.observe(this@StationsScreen) { invalidate() }
            }
        })
    }

    override fun onGetTemplate(): Template {
        val myPos    = AutoDataBridge.myPosition.value
        val allStns  = AutoDataBridge.stations.value ?: emptyList()
        val sorted   = if (myPos != null)
            allStns.sortedBy { distanceKm(myPos.lat, myPos.lon, it.lat, it.lon) }
        else allStns
        val stations = sorted.take(6) // PlaceListMapTemplate max

        val anchor = myPos?.let {
            Place.Builder(CarLocation.create(it.lat, it.lon))
                .setMarker(PlaceMarker.Builder().setColor(CarColor.GREEN).build())
                .build()
        }

        val itemList = ItemList.Builder()
        if (stations.isEmpty()) {
            itemList.setNoItemsMessage("No stations received yet")
        }

        stations.forEach { stn ->
            val distText = myPos?.let {
                val km = distanceKm(it.lat, it.lon, stn.lat, stn.lon)
                if (km < 1.0) "${(km * 1000).toInt()}m" else "${"%.1f".format(km)}km"
            }

            val ageText = ageString(stn.lastHeardMs)

            val row = Row.Builder()
                .setTitle(stn.callsign)
                .addText("${stn.comment.take(40).ifBlank { "—" }}  $ageText")
                .apply { distText?.let { addText(it) } }
                .setMetadata(
                    Metadata.Builder()
                        .setPlace(
                            Place.Builder(CarLocation.create(stn.lat, stn.lon)).build()
                        )
                        .build()
                )
                .setOnClickListener {
                    screenManager.push(StationDetailScreen(carContext, stn))
                }
                .build()

            itemList.addItem(row)
        }

        val builder = PlaceListMapTemplate.Builder()
            .setTitle("Nearby Stations (${AutoDataBridge.stations.value?.size ?: 0} total)")
            .setHeaderAction(Action.BACK)
            .setItemList(itemList.build())
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

        anchor?.let { builder.setAnchor(it) }

        return builder.build()
    }

    // ─── Haversine ───────────────────────────────────────────────────────────

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r    = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun ageString(ms: Long): String {
        val ageMin = ((System.currentTimeMillis() - ms) / 60_000L).toInt()
        return when {
            ageMin < 1    -> "just now"
            ageMin < 60   -> "${ageMin}m"
            ageMin < 1440 -> "${ageMin / 60}h"
            else          -> "${ageMin / 1440}d"
        }
    }
}

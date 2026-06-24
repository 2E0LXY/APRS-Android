package uk.aprsnet.client.auto.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import uk.aprsnet.client.auto.AutoDataBridge

class SearchScreen(
    carContext: CarContext,
    private val mode: Mode = Mode.STATION
) : Screen(carContext) {

    enum class Mode { STATION, COMPOSE }

    private var results: List<AutoDataBridge.StationData> = emptyList()

    override fun onGetTemplate(): Template {
        val itemList = ItemList.Builder()

        if (results.isEmpty()) {
            itemList.setNoItemsMessage(
                if (mode == Mode.COMPOSE) "Search callsign to message"
                else "Type to search heard stations"
            )
        }

        results.take(6).forEach { stn ->
            val ageMin = ((System.currentTimeMillis() - stn.lastHeardMs) / 60_000L).toInt()
            val ageStr = if (ageMin < 60) "${ageMin}m" else "${ageMin / 60}h"

            itemList.addItem(
                Row.Builder()
                    .setTitle(stn.callsign)
                    .addText("${stn.comment.take(40).ifBlank { "—" }}  $ageStr ago")
                    .setOnClickListener {
                        when (mode) {
                            Mode.STATION -> screenManager.push(StationDetailScreen(carContext, stn))
                            Mode.COMPOSE -> screenManager.push(MessageComposeScreen(carContext, stn.callsign))
                        }
                    }
                    .build()
            )
        }

        val hint = when (mode) {
            Mode.STATION -> "Search callsign…"
            Mode.COMPOSE -> "Search callsign to message…"
        }

        return SearchTemplate.Builder(
            object : SearchTemplate.SearchCallback {
                override fun onSearchTextChanged(searchText: String) {
                    val q = searchText.uppercase().trim()
                    results = if (q.length >= 2) {
                        AutoDataBridge.stations.value
                            ?.filter { it.callsign.contains(q) }
                            ?.sortedBy { it.callsign }
                            ?: emptyList()
                    } else emptyList()
                    invalidate()
                }

                override fun onSearchSubmitted(searchText: String) {
                    // Already shown inline — first result auto-selected if user hits enter
                    results.firstOrNull()?.let { stn ->
                        when (mode) {
                            Mode.STATION -> screenManager.push(StationDetailScreen(carContext, stn))
                            Mode.COMPOSE -> screenManager.push(MessageComposeScreen(carContext, stn.callsign))
                        }
                    }
                }
            }
        )
            .setHeaderAction(Action.BACK)
            .setSearchHint(hint)
            .setItemList(itemList.build())
            .build()
    }
}

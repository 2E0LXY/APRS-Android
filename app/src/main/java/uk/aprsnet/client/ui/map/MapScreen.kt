package uk.aprsnet.client.ui.map

import android.preference.PreferenceManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.aprs.Symbols
import uk.aprsnet.client.model.Station

/**
 * The live APRS map. Uses osmdroid, which renders the same OpenStreetMap
 * tiles the website's Leaflet map uses. Station markers update live from
 * the WebSocket feed exposed by AprsViewModel.
 */
@Composable
fun MapScreen(vm: AprsViewModel, modifier: Modifier = Modifier) {
    val stations by vm.stations.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().load(
                    ctx, PreferenceManager.getDefaultSharedPreferences(ctx)
                )
                Configuration.getInstance().userAgentValue = "APRSNetAndroid/2.0"
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(8.0)
                    controller.setCenter(GeoPoint(53.7, -1.5))
                }
            },
            update = { map ->
                // Redraw markers from the current station set
                map.overlays.clear()
                stations.values.forEach { st ->
                    map.overlays.add(makeMarker(map, st))
                }
                map.invalidate()
            }
        )
    }
}

private fun makeMarker(map: MapView, st: Station): Marker {
    return Marker(map).apply {
        position = GeoPoint(st.lat, st.lon)
        title = st.callsign
        snippet = buildString {
            append(Symbols.describe(st.symbolTable, st.symbolCode))
            if (st.comment.isNotEmpty()) append("\n").append(st.comment)
        }
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    }
}
package uk.aprsnet.client.ui.map

import android.preference.PreferenceManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.aprs.Symbols
import uk.aprsnet.client.model.Station

/**
 * The live APRS map (osmdroid - same OpenStreetMap tiles as the website).
 * Stage 3: also draws the user's own GPS position with a distinct marker,
 * and a button to centre on it / trigger an immediate beacon.
 */
@Composable
fun MapScreen(vm: AprsViewModel, modifier: Modifier = Modifier) {
    val stations by vm.stations.collectAsState()
    val myPos by vm.myPosition.collectAsState()
    val mapRef = remember { mutableStateOf<MapView?>(null) }

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
                    mapRef.value = this
                }
            },
            update = { map ->
                map.overlays.clear()
                stations.values.forEach { st -> map.overlays.add(makeMarker(map, st)) }
                // the user's own position marker, drawn last so it sits on top
                myPos?.let { fix ->
                    map.overlays.add(makeMyMarker(map, fix.lat, fix.lon))
                }
                map.invalidate()
            }
        )

        // centre-on-me button
        FloatingActionButton(
            onClick = {
                myPos?.let { fix ->
                    mapRef.value?.controller?.animateTo(GeoPoint(fix.lat, fix.lon))
                    mapRef.value?.controller?.setZoom(14.0)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "My location")
        }
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

private fun makeMyMarker(map: MapView, lat: Double, lon: Double): Marker {
    return Marker(map).apply {
        position = GeoPoint(lat, lon)
        title = "My position"
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    }
}
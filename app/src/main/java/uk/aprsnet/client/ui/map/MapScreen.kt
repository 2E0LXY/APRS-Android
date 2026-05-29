package uk.aprsnet.client.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.aprs.Symbols
import uk.aprsnet.client.model.Station

/**
 * The live APRS map (osmdroid - same OpenStreetMap tiles as the website).
 *
 * Marker maintenance is INCREMENTAL: we keep a Map<callsign, Marker> and
 * only add new markers / move existing ones. We never call overlays.clear()
 * on the hot path - doing so for every incoming packet was the cause of
 * the v2.0.1 mid-rendering crash (UI thread flooded + ConcurrentModification
 * between the WebSocket emitter and the Compose recomposition).
 *
 * Updates are sampled to at most ~5/s to keep the UI responsive even on a
 * very busy server feed.
 */
@Composable
fun MapScreen(vm: AprsViewModel, modifier: Modifier = Modifier) {
    val mapState = remember { mutableStateOf<MapView?>(null) }
    val markers = remember { HashMap<String, Marker>() }
    val myMarker = remember { mutableStateOf<Marker?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                runCatching {
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(8.0)
                        controller.setCenter(GeoPoint(53.7, -1.5))
                        mapState.value = this
                    }
                }.getOrElse { MapView(ctx) }
            }
        )

        FloatingActionButton(
            onClick = {
                val map = mapState.value ?: return@FloatingActionButton
                val fix = vm.myPosition.value ?: return@FloatingActionButton
                runCatching {
                    map.controller.animateTo(GeoPoint(fix.lat, fix.lon))
                    map.controller.setZoom(14.0)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "My location")
        }
    }

    // --- station markers: incremental, sampled to <= 5/sec ---
    LaunchedEffect(mapState.value) {
        val map = mapState.value ?: return@LaunchedEffect
        vm.stations
            .sample(200)                        // batch rapid updates
            .distinctUntilChanged()
            .collect { stations ->
                runCatching {
                    syncMarkers(map, markers, stations.values)
                    map.invalidate()
                }
            }
    }

    // --- own-position marker: separate, simple ---
    LaunchedEffect(mapState.value) {
        val map = mapState.value ?: return@LaunchedEffect
        vm.myPosition.collect { fix ->
            runCatching {
                if (fix == null) {
                    myMarker.value?.let { map.overlays.remove(it) }
                    myMarker.value = null
                } else {
                    val m = myMarker.value
                    if (m == null) {
                        val nm = Marker(map).apply {
                            position = GeoPoint(fix.lat, fix.lon)
                            title = "My position"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        myMarker.value = nm
                        map.overlays.add(nm)
                    } else {
                        m.position = GeoPoint(fix.lat, fix.lon)
                    }
                    map.invalidate()
                }
            }
        }
    }

    // --- clean up when the screen leaves composition ---
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                mapState.value?.overlays?.clear()
                markers.clear()
                myMarker.value = null
                mapState.value?.onDetach()
            }
        }
    }
}

/**
 * Reconcile the overlay list with the current station set. New stations get
 * a new Marker; existing ones have their position/title updated in place.
 * Stations that have aged out aren't removed here - we keep them until the
 * ViewModel evicts them, mirroring the website's behaviour.
 */
private fun syncMarkers(
    map: MapView,
    markers: HashMap<String, Marker>,
    stations: Collection<Station>
) {
    stations.forEach { st ->
        val existing = markers[st.callsign]
        if (existing == null) {
            val m = Marker(map).apply {
                position = GeoPoint(st.lat, st.lon)
                title = st.callsign
                snippet = buildSnippet(st)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            markers[st.callsign] = m
            map.overlays.add(m)
        } else {
            val gp = GeoPoint(st.lat, st.lon)
            if (existing.position != gp) existing.position = gp
            existing.snippet = buildSnippet(st)
        }
    }
}

private fun buildSnippet(st: Station): String = buildString {
    append(Symbols.describe(st.symbolTable, st.symbolCode))
    if (st.comment.isNotEmpty()) append("\n").append(st.comment)
}
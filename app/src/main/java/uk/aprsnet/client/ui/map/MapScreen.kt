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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import uk.aprsnet.client.model.Station
import uk.aprsnet.client.ui.common.StationDetailDialog

/**
 * The live APRS map (osmdroid - same OSM tiles as the website).
 * Marker maintenance is incremental and update-rate is sampled to keep the
 * UI responsive on a busy feed. Tapping a marker opens a station-detail
 * dialog with a Send-message button.
 *
 * Tiles are rendered with setTilesScaledToDpi(true) so place-name labels
 * are legible on high-DPI displays.
 */
@Composable
fun MapScreen(
    vm: AprsViewModel,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val mapState = remember { mutableStateOf<MapView?>(null) }
    val markers = remember { HashMap<String, Marker>() }
    val myMarker = remember { mutableStateOf<Marker?>(null) }
    var selected by remember { mutableStateOf<Station?>(null) }
    val myPos by vm.myPosition.collectAsState()
    val stations by vm.stations.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                runCatching {
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        // make place-name labels readable on high-DPI screens
                        isTilesScaledToDpi = true
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

    // station markers - sampled, incremental, with click-to-detail
    LaunchedEffect(mapState.value) {
        val map = mapState.value ?: return@LaunchedEffect
        vm.stations
            .sample(200)
            .distinctUntilChanged()
            .collect { current ->
                runCatching {
                    syncMarkers(map, markers, current.values) { call ->
                        selected = current[call]
                    }
                    map.invalidate()
                }
            }
    }

    // own-position marker
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

    // station-detail dialog (shown when a marker is tapped)
    selected?.let { st ->
        StationDetailDialog(
            station = st,
            myPos = myPos,
            onSendMessage = onSendMessage,
            onAddContact = { vm.addContact(it) },
            onDismiss = { selected = null }
        )
    }
}

/** Incremental sync: add new markers, update existing ones in place. */
private fun syncMarkers(
    map: MapView,
    markers: HashMap<String, Marker>,
    stations: Collection<Station>,
    onClick: (String) -> Unit
) {
    stations.forEach { st ->
        val existing = markers[st.callsign]
        if (existing == null) {
            val m = Marker(map).apply {
                position = GeoPoint(st.lat, st.lon)
                title = st.callsign
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> onClick(st.callsign); true }
            }
            markers[st.callsign] = m
            map.overlays.add(m)
        } else {
            val gp = GeoPoint(st.lat, st.lon)
            if (existing.position != gp) existing.position = gp
        }
    }
}
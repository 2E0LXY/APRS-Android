package uk.aprsnet.client.ui.map

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import uk.aprsnet.client.model.StationType
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
@OptIn(ExperimentalFoundationApi::class)
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

        // My-location FAB:
        //   tap     - centre map on current GPS fix
        //   long-press - force an immediate position beacon (manual override
        //                of smart-beacon's stationary-rate timer)
        FloatingActionButton(
            onClick = {},     // handled by the inner combinedClickable below
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        val map = mapState.value ?: return@combinedClickable
                        val fix = vm.myPosition.value ?: return@combinedClickable
                        runCatching {
                            map.controller.animateTo(GeoPoint(fix.lat, fix.lon))
                            map.controller.setZoom(14.0)
                        }
                    },
                    onLongClick = { vm.beaconNow() }
                )
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
                    val s = vm.settings
                    val filtered = current.values.filter { st ->
                        when (st.type) {
                            uk.aprsnet.client.model.StationType.HAM -> s.showHam
                            uk.aprsnet.client.model.StationType.WEATHER -> s.showWeather
                            uk.aprsnet.client.model.StationType.GLIDER -> s.showGlider
                            uk.aprsnet.client.model.StationType.SHIP -> s.showShip
                            uk.aprsnet.client.model.StationType.LORA -> s.showLora
                            uk.aprsnet.client.model.StationType.OBJECT,
                            uk.aprsnet.client.model.StationType.OTHER -> s.showOther
                        }
                    }
                    syncMarkers(map, markers, filtered, hiddenCalls = current.keys - filtered.map { it.callsign }.toSet()) { call ->
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
                            icon = myDotIcon(map)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
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
    hiddenCalls: Set<String> = emptySet(),
    onClick: (String) -> Unit
) {
    // Remove markers for stations now hidden by a filter
    hiddenCalls.forEach { call ->
        markers.remove(call)?.let { map.overlays.remove(it) }
    }
    stations.forEach { st ->
        val existing = markers[st.callsign]
        if (existing == null) {
            val m = Marker(map).apply {
                position = GeoPoint(st.lat, st.lon)
                title = st.callsign
                icon = dotIcon(map, st.type)
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

// ---------------------------------------------------------------------------
// Marker-icon helpers: small coloured circles per station type, drawn into
// a Bitmap so the marker doesn't fall back to osmdroid's default hand icon.
// Cached per type so we don't redraw on every marker creation.
// ---------------------------------------------------------------------------
private val typeColours = mapOf(
    StationType.HAM     to 0xFF3B82F6.toInt(),  // blue
    StationType.WEATHER to 0xFF22C55E.toInt(),  // green
    StationType.GLIDER  to 0xFFF59E0B.toInt(),  // amber
    StationType.OBJECT  to 0xFFE5E7EB.toInt(),  // light grey
    StationType.SHIP    to 0xFF06B6D4.toInt(),  // cyan
    StationType.LORA    to 0xFFA855F7.toInt(),  // purple
    StationType.OTHER   to 0xFF94A3B8.toInt()   // slate
)
private val iconCache = HashMap<Int, BitmapDrawable>()

private fun dotIcon(map: MapView, type: StationType): BitmapDrawable {
    val colour = typeColours[type] ?: 0xFF94A3B8.toInt()
    iconCache[colour]?.let { return it }
    val d = map.context.resources.displayMetrics.density
    val sizePx = (14 * d).toInt()           // 14dp dot
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val r = sizePx / 2f
    val outline = Paint().apply {
        isAntiAlias = true; color = AColor.WHITE; style = Paint.Style.FILL
    }
    val fill = Paint().apply {
        isAntiAlias = true; color = colour; style = Paint.Style.FILL
    }
    canvas.drawCircle(r, r, r, outline)
    canvas.drawCircle(r, r, r - 2f * d, fill)
    val drawable = BitmapDrawable(map.context.resources, bmp)
    iconCache[colour] = drawable
    return drawable
}

private fun myDotIcon(map: MapView): BitmapDrawable {
    val key = 0x00BFFF
    iconCache[key]?.let { return it }
    val d = map.context.resources.displayMetrics.density
    val sizePx = (20 * d).toInt()           // larger 20dp dot for own marker
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val r = sizePx / 2f
    val outline = Paint().apply {
        isAntiAlias = true; color = AColor.WHITE; style = Paint.Style.FILL
    }
    val fill = Paint().apply {
        isAntiAlias = true; color = 0xFF0EA5E9.toInt(); style = Paint.Style.FILL
    }
    canvas.drawCircle(r, r, r, outline)
    canvas.drawCircle(r, r, r - 3f * d, fill)
    val drawable = BitmapDrawable(map.context.resources, bmp)
    iconCache[key] = drawable
    return drawable
}

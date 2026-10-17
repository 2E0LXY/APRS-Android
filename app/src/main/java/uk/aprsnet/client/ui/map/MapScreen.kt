package uk.aprsnet.client.ui.map

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.graphics.toArgb
import uk.aprsnet.client.model.WxData
import uk.aprsnet.client.ui.common.weatherCondition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
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
import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer
import org.osmdroid.bonuspack.clustering.StaticCluster
import org.osmdroid.views.overlay.Marker
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.model.Station
import uk.aprsnet.client.ui.common.StationDetailDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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
    active: Boolean = true,
    modifier: Modifier = Modifier,
    // True on a "no notification" cold launch - once the first GPS fix
    // arrives the map re-centres on the user instead of the hardcoded
    // default centre, then this is consumed via onCentredOnUser().
    centreOnUserLocation: Boolean = false,
    onCentredOnUser: () -> Unit = {},
    // Set when a callsign is tapped in the message thread header - centres
    // the map on that station and opens its detail dialog, then is
    // consumed via onFocusConsumed().
    focusCallsign: String? = null,
    onFocusConsumed: () -> Unit = {}
) {
    val mapState = remember { mutableStateOf<MapView?>(null) }
    val markers = remember { HashMap<String, Marker>() }
    // Clusterer overlay holds the markers; OSM tiles render under it. At lower
    // zoom dense markers collapse into a count bubble; pinch-zoom auto-splits.
    val clusterer = remember { mutableStateOf<RadiusMarkerClusterer?>(null) }
    val myMarker = remember { mutableStateOf<Marker?>(null) }
    var selected by remember { mutableStateOf<Station?>(null) }
    var selectedCluster by remember { mutableStateOf<List<org.osmdroid.views.overlay.Marker>?>(null) }
    val myPos by vm.myPosition.collectAsState()
    val stations by vm.stations.collectAsState()
    val filterTick by vm.filterTick.collectAsState()
    var showFilters by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.visibility = if (active) View.VISIBLE else View.INVISIBLE },
            factory = { ctx ->
                runCatching {
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        // make place-name labels readable on high-DPI screens
                        isTilesScaledToDpi = true
                        controller.setZoom(8.0)
                        controller.setCenter(GeoPoint(53.7, -1.5))
                        // add clusterer overlay - markers will be added to it
                        val c = TappableClusterer(ctx) { markersInCluster ->
                            // Cluster tap -> list dialog instead of auto-zoom,
                            // which fails when markers are coincident.
                            selectedCluster = markersInCluster
                        }
                        c.setRadius(100)
                        overlays.add(c)
                        clusterer.value = c
                        mapState.value = this
                    }
                }.getOrElse { MapView(ctx) }
            }
        )

        // On a "no notification" cold launch, swap the hardcoded default
        // centre for the user's own GPS fix as soon as one arrives.
        LaunchedEffect(centreOnUserLocation, myPos) {
            if (centreOnUserLocation && myPos != null) {
                val map = mapState.value
                val fix = myPos
                if (map != null && fix != null) {
                    runCatching {
                        map.controller.setCenter(GeoPoint(fix.lat, fix.lon))
                        map.controller.setZoom(12.0)
                    }
                    onCentredOnUser()
                }
            }
        }

        // Tapping a callsign in the message thread header navigates here
        // with focusCallsign set - centre on that station and pop its
        // detail dialog open, same as tapping its marker would.
        LaunchedEffect(focusCallsign, stations) {
            val call = focusCallsign
            if (call != null) {
                val st = stations[call]
                val map = mapState.value
                if (st != null && map != null) {
                    runCatching {
                        map.controller.setCenter(GeoPoint(st.lat, st.lon))
                        map.controller.setZoom(14.0)
                    }
                    selected = st
                    onFocusConsumed()
                }
            }
        }

        // My-location FAB:
        //   tap     - centre map on current GPS fix
        //   long-press - force an immediate position beacon (manual override)
        //
        // FloatingActionButton consumes touches via its own onClick, so a
        // .combinedClickable on the modifier is shadowed. Use the FAB's own
        // onClick for tap, and Modifier.pointerInput for long-press.
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
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { vm.beaconNow() }
                    )
                }
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "My location")
        }

        // ── Quick-filter panel ────────────────────────────────────────
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AnimatedVisibility(visible = showFilters) {
                Column {
                    // Diagnostic line - visible while the filter panel is open.
                    // Shows: total stations in memory, count after filters apply,
                    // and the current filter-tick counter. If 'total' stays at 0
                    // there's no APRS-IS data; if total > 0 but visible = 0 the
                    // filter is blocking everything; if visible > 0 but the map
                    // is empty the marker rendering pipeline is the problem.
                    val total = stations.size
                    val visible = remember(stations, filterTick) {
                        applyFilters(stations, vm).size
                    }
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xEE1E2A3F),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            "diag: $total stations | $visible visible | tick #$filterTick",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    MapFilterPanel(vm = vm)
                }
            }
            SmallFloatingActionButton(
                onClick = { showFilters = !showFilters },
                containerColor = if (showFilters)
                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                else androidx.compose.material3.MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.FilterList, contentDescription = "Filter stations")
            }
        }
    }

    // station markers – immediate filter apply on tick change, then sampled ongoing
    LaunchedEffect(mapState.value, filterTick) {
        val map = mapState.value ?: return@LaunchedEffect
        val c = clusterer.value

        // Apply filter immediately so toggle response is instant (no 200ms wait)
        runCatching {
            val snap = vm.stations.value
            val filtered = applyFilters(snap, vm)
            syncMarkers(map, markers, c, filtered,
                hiddenCalls = snap.keys - filtered.map { it.callsign }.toSet()
            ) { call -> selected = vm.stations.value[call] }
            c?.invalidate()
            map.invalidate()
        }

        // Continue sampling live packet arrivals
        vm.stations
            .sample(200)
            .distinctUntilChanged()
            .collect { current ->
                runCatching {
                    val filtered = applyFilters(current, vm)
                    syncMarkers(map, markers, c, filtered,
                        hiddenCalls = current.keys - filtered.map { it.callsign }.toSet()
                    ) { call -> selected = vm.stations.value[call] }
                    c?.invalidate()
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
    // Cluster tap dialog - list of callsigns in the cluster (better UX
    // than auto-zoom for coincident markers which can't be visually
    // separated even at max zoom).
    selectedCluster?.let { clusterMarkers ->
        val stationsByCall = stations
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedCluster = null },
            title = { androidx.compose.material3.Text("${clusterMarkers.size} stations here") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    items(clusterMarkers) { mk ->
                        val call = mk.title ?: "?"
                        val st = stationsByCall[call]
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCluster = null
                                    if (st != null) selected = st
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            // Sprite icon - falls back to a coloured initial if unknown
                            if (st != null) {
                                uk.aprsnet.client.ui.common.AprsSymbolIcon(
                                    table = st.symbolTable,
                                    code = st.symbolCode,
                                    size = 24.dp
                                )
                                androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                            }
                            androidx.compose.material3.Text(
                                call,
                                modifier = Modifier.weight(1f),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            if (st != null) {
                                androidx.compose.material3.Text(
                                    uk.aprsnet.client.aprs.Symbols.describe(st.symbolTable, st.symbolCode),
                                    color = uk.aprsnet.client.ui.theme.TextDim,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { selectedCluster = null }) {
                    androidx.compose.material3.Text("Close")
                }
            }
        )
    }

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
/**
 * Sync station markers with the clusterer.
 *
 * Strategy: the markers HashMap is the single source of truth for marker
 * identity (so click handlers and other Marker state persist across calls).
 * The clusterer's items list is rebuilt fresh from the HashMap each sync.
 * This is O(n) per sync but bounded by the upstream .sample(200) so it
 * runs at most 5x/sec. Defensive against any drift between the two stores
 * - which is what caused the v2.3.1/v2.3.2 'cluster shows 2 but only 1
 * marker visible after zoom' bug.
 */
private fun syncMarkers(
    map: MapView,
    markers: HashMap<String, Marker>,
    clusterer: RadiusMarkerClusterer?,
    stations: Collection<Station>,
    hiddenCalls: Set<String> = emptySet(),
    onClick: (String) -> Unit
) {
    // 1. drop markers for filter-hidden stations
    hiddenCalls.forEach { call -> markers.remove(call) }

    // 2. add or update each visible station's marker
    stations.forEach { st ->
        val existing = markers[st.callsign]
        if (existing == null) {
            val m = Marker(map).apply {
                position = GeoPoint(st.lat, st.lon)
                title = st.callsign
                icon = dotIcon(map, st.type, st.symbolTable, st.symbolCode, st.wx)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> onClick(st.callsign); true }
            }
            markers[st.callsign] = m
        } else {
            val gp = GeoPoint(st.lat, st.lon)
            if (existing.position != gp) existing.position = gp
            // refresh icon too, in case station type/symbol changed
            existing.icon = dotIcon(map, st.type, st.symbolTable, st.symbolCode, st.wx)
        }
    }

    // 3. defensive rebuild of clusterer items from the HashMap (no duplicates,
    //    no stale markers, guaranteed in sync with the visible set)
    if (clusterer != null) {
        clusterer.items.clear()
        markers.values.forEach { clusterer.items.add(it) }
    }
}

// ---------------------------------------------------------------------------
// Marker-icon helpers: small coloured circles per station type, drawn into
// a Bitmap so the marker doesn't fall back to osmdroid's default hand icon.
// Cached per type so we don't redraw on every marker creation.
// ---------------------------------------------------------------------------
private val iconCache = HashMap<Int, BitmapDrawable>()

private val glyphIconCache = HashMap<String, BitmapDrawable>()

/**
 * Marker icon: 20dp coloured circle with the curated APRS glyph centred on it.
 * Cached per (colour, glyph) pair. Falls back to a plain dot when no glyph
 * is known for the symbol pair.
 */
/**
 * Buckets a WxData reading into a short string used both for cache keys and
 * to decide the marker's colour/glyph. Mirrors weatherCondition()'s logic
 * at a coarser level so subtle reading changes (e.g. 61F vs 62F) don't
 * thrash the icon cache.
 */
private fun wxConditionKey(wx: WxData?): String {
    if (wx == null) return "none"
    val rain = (wx.rain1hIn ?: 0.0) > 0.0
    val windy = (wx.gustMph ?: wx.windSpeedMph ?: 0.0) >= 20.0
    val t = wx.tempF
    return when {
        rain && windy                  -> "storm"
        rain && t != null && t <= 32.0 -> "snow"
        rain                            -> "rain"
        t != null && t <= 32.0          -> "snow"
        windy                           -> "wind"
        t != null && t >= 75.0          -> "hot"
        t != null && t >= 50.0          -> "mild"
        else                            -> "cloud"
    }
}

/** Station marker size on the map. The official symbol is drawn alone - no coloured disc. */
private const val MARKER_DP = 30

/**
 * Marker icon: the official APRS symbol (with overlay character when the packet carries one).
 * Stations that never sent a valid symbol get the official default for their type. Weather
 * stations keep the official weather symbol and add a small conditions badge in the corner.
 */
private fun dotIcon(
    map: MapView,
    type: StationType,
    symbolTable: Char,
    symbolCode: Char,
    wx: WxData? = null
): BitmapDrawable {
    val ctx = map.context
    val (tbl, cod) = if (uk.aprsnet.client.aprs.AprsSymbols.isValid(symbolTable, symbolCode))
        symbolTable to symbolCode
    else uk.aprsnet.client.aprs.AprsSymbols.defaultFor(type)
    val wxKey = if (type == StationType.WEATHER && wx != null) wxConditionKey(wx) else null
    val key = "${uk.aprsnet.client.aprs.AprsSymbols.normalize(tbl, cod)}${wxKey ?: ""}"
    glyphIconCache[key]?.let { return it }

    val d = ctx.resources.displayMetrics.density
    val sizePx = (MARKER_DP * d).toInt()
    val symbol = uk.aprsnet.client.aprs.AprsSymbols.bitmap(ctx, tbl, cod, sizePx)

    val drawable = if (wxKey == null || wx == null) {
        BitmapDrawable(ctx.resources, symbol)
    } else {
        val bmp = symbol.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)
        val (emoji, tint) = weatherCondition(wx)
        val br = 6.5f * d
        val cx = sizePx - br
        val cy = br
        canvas.drawCircle(cx, cy, br, Paint().apply { isAntiAlias = true; color = AColor.WHITE })
        canvas.drawCircle(cx, cy, br - 1.5f * d, Paint().apply { isAntiAlias = true; color = tint.toArgb() })
        val txt = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; textSize = 8f * d }
        val fm = txt.fontMetrics
        canvas.drawText(emoji, cx, cy - (fm.ascent + fm.descent) / 2f, txt)
        BitmapDrawable(ctx.resources, bmp)
    }
    glyphIconCache[key] = drawable
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
// ---------------------------------------------------------------------------
// TappableClusterer
// Intercepts cluster taps so we can show a list of callsigns instead of the
// default zoom-to-bounding-box behaviour. The default behaviour is unhelpful
// when two or more markers are coincident (or within ~100px even at max zoom)
// because zooming doesn't visually separate them - the user sees count 2 but
// only one pin after zoom.
// ---------------------------------------------------------------------------
private class TappableClusterer(
    ctx: Context,
    private val onClusterTap: (List<Marker>) -> Unit
) : RadiusMarkerClusterer(ctx) {

    override fun buildClusterMarker(cluster: StaticCluster?, mapView: MapView?): Marker {
        val m = super.buildClusterMarker(cluster, mapView)
        // Override the cluster icon's click handler. cluster.items holds the
        // member Markers; we forward those to the dialog. Returning true
        // consumes the touch so the default zoom-to-bbox doesn't also run.
        m.setOnMarkerClickListener { _, _ ->
            val items: List<Marker> = (0 until (cluster?.size ?: 0))
                .mapNotNull { cluster?.getItem(it) }
            // Always forward to the dialog. For size-1 clusters the dialog
            // shows a single row that opens the station detail when tapped -
            // slightly redundant but avoids calling protected APIs on Marker
            // from outside its package.
            if (items.isNotEmpty()) onClusterTap(items)
            true
        }
        return m
    }
}

// ============================================================================
// Filter helpers
// ============================================================================
private fun applyFilters(
    current: Map<String, uk.aprsnet.client.model.Station>,
    vm: AprsViewModel
): List<uk.aprsnet.client.model.Station> {
    val s = vm.settings
    val myCalls = setOf(s.callsign.uppercase(), s.fullCallsign.uppercase())
    val myFix   = vm.myPosition.value
    val maxKm   = s.filterRadiusKm
    return current.values
        .filter { st -> st.callsign.uppercase() !in myCalls }
        .filter { st ->
            // Per-member drop filters (synced from server on member login).
            // Same pattern set as web map _memberDropsStation() and the
            // server-side shouldRouteOutbound() admin Drop Filters.
            val raw = st.raw.uppercase()
            if (s.dropPistar && (
                    raw.contains("PISTAR") || raw.contains("MMDVM") ||
                    raw.contains("APDPRS") || raw.contains("APDG") ||
                    raw.contains("APIRCD") || raw.contains("IRCDDB"))) return@filter false
            if (s.dropDstar  && (raw.contains("D-STAR") || raw.contains("APDSTR"))) return@filter false
            if (s.dropApdesk &&  raw.contains("APDESK")) return@filter false
            true
        }
        .filter { st ->
            when (st.type) {
                StationType.HAM     -> s.showHam
                StationType.WEATHER -> s.showWeather
                StationType.GLIDER  -> s.showGlider
                StationType.SHIP    -> s.showShip
                StationType.LORA    -> s.showLora
                StationType.MMDVM   -> s.showMmdvm
                StationType.OBJECT,
                StationType.OTHER   -> s.showOther
            }
        }
        .filter { st ->
            maxKm == 0 || myFix == null ||
                haversine(myFix.lat, myFix.lon, st.lat, st.lon) <= maxKm
        }
}

private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val R = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return R * 2 * asin(sqrt(a))
}

// ============================================================================
// Map quick-filter overlay
// ============================================================================
@androidx.compose.runtime.Composable
private fun MapFilterPanel(vm: AprsViewModel) {
    val s = vm.settings
    var ham    by remember { mutableStateOf(s.showHam) }
    var wx     by remember { mutableStateOf(s.showWeather) }
    var ship   by remember { mutableStateOf(s.showShip) }
    var glider by remember { mutableStateOf(s.showGlider) }
    var lora   by remember { mutableStateOf(s.showLora) }
    var mmdvm  by remember { mutableStateOf(s.showMmdvm) }
    var other  by remember { mutableStateOf(s.showOther) }
    val distOptions = listOf(0 to "All", 50 to "50km", 100 to "100km", 250 to "250km", 500 to "500km")
    var distIdx by remember {
        mutableStateOf(distOptions.indexOfFirst { it.first == s.filterRadiusKm }.coerceAtLeast(0))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xEE1E2A3F),
        modifier = Modifier.padding(bottom = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Station types", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MapChip(ham,    "HAM",     0xFF3B82F6) { ham    = !ham;    s.showHam     = ham;    vm.tickFilters() }
                MapChip(wx,     "WX",      0xFF22C55E) { wx     = !wx;     s.showWeather = wx;     vm.tickFilters() }
                MapChip(ship,   "Ships",   0xFF06B6D4) { ship   = !ship;   s.showShip    = ship;   vm.tickFilters() }
                MapChip(glider, "Gliders", 0xFFF59E0B) { glider = !glider; s.showGlider  = glider; vm.tickFilters() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MapChip(lora,  "LoRa",  0xFFA855F7) { lora  = !lora;  s.showLora  = lora;  vm.tickFilters() }
                MapChip(mmdvm, "MMDVM", 0xFFEC4899) { mmdvm = !mmdvm; s.showMmdvm = mmdvm; vm.tickFilters() }
                MapChip(other, "Other", 0xFF94A3B8) { other = !other; s.showOther = other; vm.tickFilters() }
            }
            Text("Distance", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                distOptions.forEachIndexed { idx, (km, label) ->
                    MapChip(distIdx == idx, label, 0xFF4B6080) {
                        distIdx = idx
                        s.filterRadiusKm = km
                        vm.tickFilters()
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MapChip(selected: Boolean, label: String, activeColor: Long, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(activeColor) else Color(0x44FFFFFF),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

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
    modifier: Modifier = Modifier
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
private val typeColours = mapOf(
    StationType.HAM     to 0xFF3B82F6.toInt(),  // blue
    StationType.WEATHER to 0xFF22C55E.toInt(),  // green
    StationType.GLIDER  to 0xFFF59E0B.toInt(),  // amber
    StationType.OBJECT  to 0xFFE5E7EB.toInt(),  // light grey
    StationType.SHIP    to 0xFF06B6D4.toInt(),  // cyan
    StationType.LORA    to 0xFFA855F7.toInt(),  // purple
    StationType.MMDVM   to 0xFFEC4899.toInt(),  // rose / magenta
    StationType.OTHER   to 0xFF94A3B8.toInt()   // slate
)
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

private fun dotIcon(
    map: MapView,
    type: StationType,
    symbolTable: Char,
    symbolCode: Char,
    wx: WxData? = null
): BitmapDrawable {
    // Weather stations: tint the marker by current conditions (rain, snow,
    // storm, wind, hot, mild, cloudy) instead of a flat colour, and draw the
    // matching emoji as the glyph so conditions are visible at a glance.
    val wxKey = if (type == StationType.WEATHER) wxConditionKey(wx) else null
    val (colour, wxEmoji) = if (type == StationType.WEATHER && wx != null) {
        val (emoji, tint) = weatherCondition(wx)
        tint.toArgb() to emoji
    } else {
        (typeColours[type] ?: 0xFF94A3B8.toInt()) to null
    }
    // Cache key includes the symbol (and WX condition bucket, if weather) so
    // a station changing symbol or conditions gets a fresh icon.
    val key = "${colour}_${symbolTable}${symbolCode}${wxKey ?: ""}"
    glyphIconCache[key]?.let { return it }

    val d = map.context.resources.displayMetrics.density
    val sizePx = (24 * d).toInt()                // 24dp - was 20dp
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

    // Weather stations: draw the condition emoji (rain/snow/sun/etc.) in
    // place of the generic APRS sprite, so the marker itself communicates
    // current conditions.
    if (wxEmoji != null) {
        val txt = Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 13f * d
        }
        val fm = txt.fontMetrics
        canvas.drawText(wxEmoji, r, r - (fm.ascent + fm.descent) / 2f, txt)
        val drawable = BitmapDrawable(map.context.resources, bmp)
        glyphIconCache[key] = drawable
        return drawable
    }

    // Prefer the real APRS sprite over our 3-letter glyph fallback. The
    // sprite is drawn slightly smaller than the inner fill so the colour
    // ring stays visible at low zoom (type-colour cue) and the recognisable
    // APRS symbol shows at high zoom.
    val sprite = uk.aprsnet.client.aprs.AprsSymbols.bitmap(
        map.context, symbolTable, symbolCode)
    if (sprite != null) {
        val spriteSize = (18 * d)        // 18dp sprite inside the 24dp circle
        val left = r - spriteSize / 2f
        val top  = r - spriteSize / 2f
        val dst = android.graphics.RectF(left, top, left + spriteSize, top + spriteSize)
        val src = android.graphics.Rect(0, 0, sprite.width, sprite.height)
        canvas.drawBitmap(sprite, src, dst, Paint().apply { isAntiAlias = true; isFilterBitmap = true })
    } else {
        // Fallback: 3-letter glyph for symbols not in the sheet (shouldn't
        // really happen but keeps unknown symbols readable).
        val glyph = aprsGlyph(symbolTable, symbolCode)
        if (glyph != null) {
            val txt = Paint().apply {
                isAntiAlias = true; color = AColor.WHITE
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
                textSize = 9f * d
            }
            val fm = txt.fontMetrics
            canvas.drawText(glyph, r, r - (fm.ascent + fm.descent) / 2f, txt)
        }
    }

    val drawable = BitmapDrawable(map.context.resources, bmp)
    glyphIconCache[key] = drawable
    return drawable
}

/**
 * Curated APRS-symbol-to-glyph table. Maps the most common (table, code)
 * pairs from the APRS symbol set to a short readable string drawn on the
 * marker. Unknown pairs return null so the marker stays a plain dot.
 * Reference: APRS Spec 1.0.1 Chapter 20 (Symbol Tables)
 */
private fun aprsGlyph(table: Char, code: Char): String? {
    if (table != '/' && table != '\\') return null
    return APRS_GLYPHS[code]
}

private val APRS_GLYPHS: Map<Char, String> = mapOf(
    '>' to "CAR",   '<' to "M/C",   '-' to "QTH",  ':' to "FD",
    ';' to "CMP",   '=' to "RR",    '?' to "?",    '@' to "HUR",
    '#' to "DIG",   '&' to "GW",    '_' to "WX",   '`' to "DSH",
    '^' to "AIR",   '\'' to "AIR",  '!' to "POL",
    'A' to "AID",   'C' to "CAN",   'E' to "EYE",  'F' to "FRM",
    'H' to "HTL",   'K' to "SCH",   'L' to "PC",   'N' to "NTS",
    'O' to "BAL",   'P' to "POL",   'R' to "RV",   'S' to "SHU",
    'U' to "BUS",   'W' to "NWS",   'X' to "HEL",  'Y' to "YAC",
    'a' to "AMB",   'b' to "BIKE",  'd' to "FRE",  'e' to "HRS",
    'f' to "TRK",   'g' to "GLD",   'h' to "HSP",  'j' to "JEEP",
    'k' to "TRK",   'l' to "LAP",   'n' to "NODE", 'o' to "EOC",
    'r' to "RPT",   's' to "SHIP",  't' to "STOP", 'u' to "18W",
    'v' to "VAN",   'w' to "WTR",   'y' to "YAGI", '[' to "JOG"
)

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

package uk.aprsnet.client.auto.renderer

import android.graphics.*
import uk.aprsnet.client.auto.AutoDataBridge
import kotlin.math.*

class APRSMapRenderer {

    // ─── Viewport ────────────────────────────────────────────────────────────

    var surfaceWidth  = 1
    var surfaceHeight = 1

    private var centerLat = 53.5   // Default: UK centre
    private var centerLon = -1.5
    private var zoomDeg   = 1.0    // Degrees of latitude visible

    private val latSpan get() = zoomDeg
    private val lonSpan get() = zoomDeg * surfaceWidth.toDouble() / surfaceHeight.toDouble()

    // ─── Paints ──────────────────────────────────────────────────────────────

    private val bgPaint = Paint().apply { color = Color.rgb(22, 34, 46) }

    private val gridPaint = Paint().apply {
        color = Color.argb(50, 120, 180, 255)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val stationPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val wxStationPaint = Paint().apply {
        color = Color.rgb(100, 200, 255)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val stalePaint = Paint().apply {
        color = Color.rgb(80, 90, 100)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val myPosPaint = Paint().apply {
        color = Color.rgb(50, 255, 100)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val myPosStrokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val callsignPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(3f, 1f, 1f, Color.BLACK)
    }

    private val uiTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }

    private val scaleBarPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val coordPaint = Paint().apply {
        color = Color.argb(160, 200, 220, 255)
        textSize = 20f
        isAntiAlias = true
    }

    // ─── Coordinate maths ────────────────────────────────────────────────────

    fun latLonToPixel(lat: Double, lon: Double): PointF {
        val x = ((lon - centerLon) / lonSpan + 0.5) * surfaceWidth
        val y = ((centerLat - lat) / latSpan + 0.5) * surfaceHeight
        return PointF(x.toFloat(), y.toFloat())
    }

    private fun pixelToLatLon(x: Float, y: Float): Pair<Double, Double> {
        val lon = (x / surfaceWidth - 0.5) * lonSpan + centerLon
        val lat = centerLat - (y / surfaceHeight - 0.5) * latSpan
        return Pair(lat, lon)
    }

    private fun isVisible(lat: Double, lon: Double): Boolean {
        val margin = 0.1
        return lat in (centerLat - latSpan * (0.5 + margin))..(centerLat + latSpan * (0.5 + margin)) &&
               lon in (centerLon - lonSpan * (0.5 + margin))..(centerLon + lonSpan * (0.5 + margin))
    }

    // ─── Viewport controls ───────────────────────────────────────────────────

    fun onSurfaceAvailable(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
    }

    fun centerOn(lat: Double, lon: Double) {
        centerLat = lat
        centerLon = lon
    }

    fun pan(dx: Float, dy: Float) {
        centerLon -= (dx / surfaceWidth) * lonSpan
        centerLat += (dy / surfaceHeight) * latSpan
    }

    fun zoom(scaleFactor: Float, focusX: Float, focusY: Float) {
        // Zoom toward focal point
        val (fLat, fLon) = pixelToLatLon(focusX, focusY)
        zoomDeg = (zoomDeg / scaleFactor).coerceIn(0.005, 80.0)
        // Re-anchor so focal point stays fixed
        val newFpt = latLonToPixel(fLat, fLon)
        centerLon += ((focusX - newFpt.x) / surfaceWidth) * lonSpan
        centerLat -= ((focusY - newFpt.y) / surfaceHeight) * latSpan
    }

    fun zoomIn()  { zoomDeg = (zoomDeg * 0.65).coerceAtLeast(0.005) }
    fun zoomOut() { zoomDeg = (zoomDeg * 1.54).coerceAtMost(80.0) }

    // ─── Rendering ───────────────────────────────────────────────────────────

    fun draw(
        canvas: Canvas,
        stations: List<AutoDataBridge.StationData>,
        myPos: AutoDataBridge.PositionData?
    ) {
        canvas.drawRect(0f, 0f, surfaceWidth.toFloat(), surfaceHeight.toFloat(), bgPaint)
        drawGrid(canvas)
        drawStations(canvas, stations)
        myPos?.let { drawMyPosition(canvas, it) }
        drawScaleBar(canvas)
        drawCentreCrosshair(canvas)
        drawHUD(canvas, stations.size, myPos)
    }

    private fun drawGrid(canvas: Canvas) {
        // Lat lines
        val latStep = latSpan / 5.0
        var lat = floor(centerLat / latStep) * latStep - latStep
        while (lat < centerLat + latSpan) {
            if (isVisible(lat, centerLon)) {
                val y = ((centerLat - lat) / latSpan + 0.5) * surfaceHeight
                canvas.drawLine(0f, y.toFloat(), surfaceWidth.toFloat(), y.toFloat(), gridPaint)
                canvas.drawText("%.3f°".format(lat), 8f, y.toFloat() - 4, coordPaint)
            }
            lat += latStep
        }
        // Lon lines
        val lonStep = lonSpan / 5.0
        var lon = floor(centerLon / lonStep) * lonStep - lonStep
        while (lon < centerLon + lonSpan) {
            if (isVisible(centerLat, lon)) {
                val x = ((lon - centerLon) / lonSpan + 0.5) * surfaceWidth
                canvas.drawLine(x.toFloat(), 0f, x.toFloat(), surfaceHeight.toFloat(), gridPaint)
                canvas.drawText("%.3f°".format(lon), x.toFloat() + 4, 22f, coordPaint)
            }
            lon += lonStep
        }
    }

    private fun drawStations(canvas: Canvas, stations: List<AutoDataBridge.StationData>) {
        val now = System.currentTimeMillis()
        stations.filter { isVisible(it.lat, it.lon) }.forEach { station ->
            val pt = latLonToPixel(station.lat, station.lon)
            val ageMs = now - station.lastHeardMs
            val isStale = ageMs > 3_600_000L    // > 1 hour
            val isWx    = station.symbol == "_"
            val paint = when {
                isStale -> stalePaint
                isWx    -> wxStationPaint
                else    -> stationPaint
            }
            val radius = if (isWx) 8f else 10f
            canvas.drawCircle(pt.x, pt.y, radius, paint)
            // Course line if moving (>2 kt)
            if (!isStale && station.speed > 2f) {
                val rad = Math.toRadians(station.course.toDouble())
                val len = 30f * (station.speed / 10f).coerceAtMost(3f)
                val ex = pt.x + (sin(rad) * len).toFloat()
                val ey = pt.y - (cos(rad) * len).toFloat()
                val linePaint = Paint().apply {
                    color = Color.YELLOW; strokeWidth = 2f; style = Paint.Style.STROKE
                }
                canvas.drawLine(pt.x, pt.y, ex, ey, linePaint)
            }
            canvas.drawText(station.callsign, pt.x + 13f, pt.y + 8f, callsignPaint)
        }
    }

    private fun drawMyPosition(canvas: Canvas, pos: AutoDataBridge.PositionData) {
        if (!isVisible(pos.lat, pos.lon)) return
        val pt = latLonToPixel(pos.lat, pos.lon)

        // Heading triangle
        val rad = Math.toRadians(pos.course.toDouble())
        val triPath = Path().apply {
            val tipX = pt.x + (sin(rad) * 22f).toFloat()
            val tipY = pt.y - (cos(rad) * 22f).toFloat()
            val lRad = rad + Math.toRadians(130.0)
            val rRad = rad - Math.toRadians(130.0)
            moveTo(tipX, tipY)
            lineTo(pt.x + (sin(lRad) * 14f).toFloat(), pt.y - (cos(lRad) * 14f).toFloat())
            lineTo(pt.x + (sin(rRad) * 14f).toFloat(), pt.y - (cos(rRad) * 14f).toFloat())
            close()
        }
        canvas.drawPath(triPath, myPosPaint)
        canvas.drawPath(triPath, myPosStrokePaint)

        // Accuracy ring (static visual only, not real accuracy)
        val ringPaint = Paint().apply {
            color = Color.argb(60, 50, 255, 100)
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawCircle(pt.x, pt.y, 30f, ringPaint)
    }

    private fun drawScaleBar(canvas: Canvas) {
        // Scale bar represents 20% of viewport height in km
        val scaleKm = latSpan * 111.0 * 0.2
        val barPx = surfaceWidth * 0.25f
        val x = 16f
        val y = surfaceHeight - 48f
        canvas.drawLine(x, y, x + barPx, y, scaleBarPaint)
        canvas.drawLine(x, y - 8f, x, y + 8f, scaleBarPaint)
        canvas.drawLine(x + barPx, y - 8f, x + barPx, y + 8f, scaleBarPaint)
        val label = if (scaleKm >= 1.0) "~${scaleKm.toInt()}km" else "~${(scaleKm * 1000).toInt()}m"
        canvas.drawText(label, x, y - 12f, uiTextPaint)
    }

    private fun drawCentreCrosshair(canvas: Canvas) {
        val cx = surfaceWidth / 2f
        val cy = surfaceHeight / 2f
        val paint = Paint().apply { color = Color.argb(100, 255, 255, 255); strokeWidth = 1f }
        canvas.drawLine(cx - 20, cy, cx + 20, cy, paint)
        canvas.drawLine(cx, cy - 20, cx, cy + 20, paint)
    }

    private fun drawHUD(canvas: Canvas, count: Int, myPos: AutoDataBridge.PositionData?) {
        val x = surfaceWidth - 16f
        val baseline = surfaceHeight - 16f
        val textR = Paint(uiTextPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("$count stations", x, baseline, textR)
        myPos?.let {
            canvas.drawText("%.4f, %.4f".format(it.lat, it.lon), x, baseline - 36f, textR)
            if (it.speed > 0.5f) {
                canvas.drawText("${it.speed.toInt()}kt ${it.course}°", x, baseline - 72f, textR)
            }
        }
    }
}

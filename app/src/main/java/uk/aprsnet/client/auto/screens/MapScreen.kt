package uk.aprsnet.client.auto.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.*
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import uk.aprsnet.client.auto.AutoDataBridge
import uk.aprsnet.client.auto.renderer.APRSMapRenderer

class MapScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private var surfaceContainer: SurfaceContainer? = null
    private val renderer = APRSMapRenderer()
    private var isTracking = true   // Auto-follow my position

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {

            override fun onStart(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java)
                    .setSurfaceCallback(this@MapScreen)
            }

            override fun onStop(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java)
                    .setSurfaceCallback(null)
                surfaceContainer = null
            }
        })

        // Lifecycle-scoped observers — auto-removed on DESTROYED
        AutoDataBridge.stations.observe(this) { renderMap() }

        AutoDataBridge.myPosition.observe(this) { pos ->
            if (isTracking && pos != null) renderer.centerOn(pos.lat, pos.lon)
            renderMap()
        }
    }

    // ─── SurfaceCallback ─────────────────────────────────────────────────────

    override fun onSurfaceAvailable(holder: SurfaceContainer) {
        surfaceContainer = holder
        renderer.onSurfaceAvailable(holder.width, holder.height)
        // Centre on my position if available, else UK default
        AutoDataBridge.myPosition.value?.let { renderer.centerOn(it.lat, it.lon) }
        renderMap()
    }

    override fun onSurfaceDestroyed(holder: SurfaceContainer) {
        surfaceContainer = null
    }

    override fun onVisibleAreaChanged(visibleArea: android.graphics.Rect) {
        // Update renderer dimensions to exclude system chrome
        renderer.onSurfaceAvailable(visibleArea.width(), visibleArea.height())
        renderMap()
    }

    override fun onSurfaceScroll(distanceX: Float, distanceY: Float) {
        isTracking = false
        renderer.pan(distanceX, distanceY)
        renderMap()
    }

    override fun onSurfaceScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        renderer.zoom(scaleFactor, focusX, focusY)
        renderMap()
    }

    // ─── Template ────────────────────────────────────────────────────────────

    override fun onGetTemplate(): Template {
        return NavigationTemplate.Builder()
            .setMapActionStrip(buildMapActions())
            .setActionStrip(buildActionStrip())
            .build()
    }

    // ─── Action strips ───────────────────────────────────────────────────────

    private fun buildMapActions(): ActionStrip {
        return ActionStrip.Builder()
            .addAction(iconAction(Color.rgb(50, 200, 100), "⌖") {
                isTracking = true
                AutoDataBridge.myPosition.value?.let { renderer.centerOn(it.lat, it.lon) }
                renderMap()
            })
            .addAction(iconAction(Color.rgb(80, 160, 255), "+") {
                renderer.zoomIn(); renderMap()
            })
            .addAction(iconAction(Color.rgb(80, 160, 255), "−") {
                renderer.zoomOut(); renderMap()
            })
            .addAction(iconAction(Color.rgb(200, 100, 50), "Stn") {
                screenManager.push(StationsScreen(carContext))
            })
            .build()
    }

    private fun buildActionStrip(): ActionStrip {
        val stationCount = AutoDataBridge.stations.value?.size ?: 0
        return ActionStrip.Builder()
            .addAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle("$stationCount Stns")
                    .setOnClickListener { screenManager.push(StationsScreen(carContext)) }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("Msgs")
                    .setOnClickListener { screenManager.push(MessagesScreen(carContext)) }
                    .build()
            )
            .build()
    }

    // ─── Rendering ───────────────────────────────────────────────────────────

    private fun renderMap() {
        val container = surfaceContainer ?: return
        val surface = container.surface ?: return
        if (!surface.isValid) return
        try {
            val canvas: Canvas = surface.lockCanvas(null) ?: return
            renderer.draw(
                canvas,
                AutoDataBridge.stations.value ?: emptyList(),
                AutoDataBridge.myPosition.value
            )
            surface.unlockCanvasAndPost(canvas)
        } catch (_: Exception) { /* Surface may be recycling */ }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun iconAction(colour: Int, label: String, onClick: () -> Unit): Action {
        val size = 80
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawCircle(size / 2f, size / 2f, size / 2f - 2,
            Paint().apply { color = colour; isAntiAlias = true })
        c.drawText(label, size / 2f, size / 2f + 10f,
            Paint().apply {
                color = Color.WHITE; textSize = 28f
                textAlign = Paint.Align.CENTER; isAntiAlias = true; isFakeBoldText = true
            })
        return Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithBitmap(bmp)).build())
            .setOnClickListener(onClick)
            .build()
    }
}

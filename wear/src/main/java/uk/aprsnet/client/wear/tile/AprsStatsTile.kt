package uk.aprsnet.client.wear.tile

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * APRS Net Tile — placeholder.
 *
 * androidx.wear.tiles:tiles:1.4 moved its layout API to
 * androidx.wear.protolayout, breaking the original implementation.
 * This stub keeps the class compilable. The manifest service declaration
 * has been removed so the tile is not surfaced to the watch face.
 *
 * Re-implement using androidx.wear.protolayout:protolayout:1.x when
 * adding the tile feature back.
 */
class AprsStatsTile : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}

package uk.aprsnet.client.wear.tile

import android.content.Context
import androidx.wear.tiles.*
import androidx.wear.tiles.material.*
import androidx.wear.tiles.material.layouts.PrimaryLayout
import com.google.common.util.concurrent.ListenableFuture
import uk.aprsnet.client.wear.data.WearDataBridge

/**
 * Tile shown when user long-presses the watch face and picks APRS Net.
 * Displays connection status, station count, and last beacon age.
 */
class AprsStatsTile : TileService() {

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return androidx.wear.tiles.TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTimeline(buildTimeline(applicationContext))
            .buildListenableFuture()
    }

    override fun onResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        return ResourceBuilders.Resources.Builder()
            .setVersion("1")
            .buildListenableFuture()
    }

    private fun buildTimeline(context: Context): TimelineBuilders.Timeline {
        val status = WearDataBridge.status.value
        val ageStr = if (status.lastBeaconTs > 0L) {
            val ago = (System.currentTimeMillis() - status.lastBeaconTs * 1000) / 60_000
            if (ago < 60) "${ago}m" else "${ago / 60}h"
        } else "—"

        val title    = Text.Builder(context, status.myCallsign.ifBlank { "APRS Net" }).build()
        val content  = Text.Builder(context,
            "${status.stationCount} stns · ${if (status.connected) "●" else "○"}"
        ).build()
        val subtext  = Text.Builder(context, "Last bcn: $ageStr").build()

        val layout = PrimaryLayout.Builder(DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp(192).setScreenHeightDp(192)
            .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
            .build())
            .setPrimaryLabelTextContent(title)
            .setContent(content)
            .setSecondaryLabelTextContent(subtext)
            .build()

        return TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(LayoutBuilders.Layout.Builder().setRoot(layout.toLayoutElement()).build())
                    .build()
            )
            .build()
    }

    private fun TileBuilders.Tile.Builder.buildListenableFuture() =
        com.google.common.util.concurrent.Futures.immediateFuture(build())

    private fun ResourceBuilders.Resources.Builder.buildListenableFuture() =
        com.google.common.util.concurrent.Futures.immediateFuture(build())
}

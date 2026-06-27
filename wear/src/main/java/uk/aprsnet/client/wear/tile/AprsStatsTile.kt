package uk.aprsnet.client.wear.tile

import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TimelineBuilders
import androidx.wear.tiles.LayoutBuilders
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.ModifiersBuilders
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import uk.aprsnet.client.wear.data.WearDataBridge

/**
 * Wear OS tile shown when the user long-presses the watch face and selects APRS Net.
 * Displays callsign, station count, and last beacon age.
 * Uses TileService directly (avoids coroutine wrapper API compatibility issues).
 */
class AprsStatsTile : androidx.wear.tiles.TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> =
        Futures.immediateFuture(buildTile())

    override fun onResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion("0").build()
        )

    private fun buildTile(): TileBuilders.Tile {
        val status  = WearDataBridge.status.value
        val ageStr  = if (status.lastBeaconTs > 0L) {
            val ago = (System.currentTimeMillis() - status.lastBeaconTs * 1000L) / 60_000L
            if (ago < 60) "${ago}m ago" else "${ago / 60}h ago"
        } else "No beacon"
        val connStr = if (status.connected) "Connected" else "Offline"
        val label   = "${status.myCallsign.ifBlank { "APRS Net" }} | ${status.stationCount} stns"

        val column = LayoutBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutBuilders.HorizontalAlignmentProp.Builder()
                .setValue(LayoutBuilders.HorizontalAlignment.HORIZONTAL_ALIGN_CENTER)
                .build())
            .addContent(text(label,   bold = true))
            .addContent(text(connStr, bold = false))
            .addContent(text(ageStr,  bold = false))
            .build()

        val layout = LayoutBuilders.Layout.Builder()
            .setRoot(
                LayoutBuilders.Box.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .setVerticalAlignment(LayoutBuilders.VerticalAlignmentProp.Builder()
                        .setValue(LayoutBuilders.VerticalAlignment.VERTICAL_ALIGN_CENTER)
                        .build())
                    .setHorizontalAlignment(LayoutBuilders.HorizontalAlignmentProp.Builder()
                        .setValue(LayoutBuilders.HorizontalAlignment.HORIZONTAL_ALIGN_CENTER)
                        .build())
                    .addContent(column)
                    .build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion("0")
            .setFreshnessIntervalMillis(30 * 60 * 1_000L) // refresh hint: 30 min
            .setTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(layout)
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun text(value: String, bold: Boolean): LayoutBuilders.Text =
        LayoutBuilders.Text.Builder()
            .setText(LayoutBuilders.StringProp.Builder(value).build())
            .setMaxLines(1)
            .build()
}

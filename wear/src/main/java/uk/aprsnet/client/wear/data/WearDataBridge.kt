package uk.aprsnet.client.wear.data

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

private const val TAG = "WearDataBridge"

// ── Data models ───────────────────────────────────────────────────────────────

data class WearStatus(
    val connected: Boolean   = false,
    val stationCount: Int    = 0,
    val lastBeaconTs: Long   = 0L,
    val myCallsign: String   = "",
    val myLat: Double        = 0.0,
    val myLon: Double        = 0.0,
    val speedKmh: Double     = 0.0,
    val course: Int          = 0
)

data class WearStation(
    val callsign: String,
    val lat: Double,
    val lon: Double,
    val comment: String,
    val lastHeardMs: Long,
    val distKm: Double
)

data class WearMessage(
    val from: String,
    val body: String,
    val ts: Long,
    val read: Boolean
)

// ── Singleton state holder ─────────────────────────────────────────────────────

object WearDataBridge {

    private val _status   = MutableStateFlow(WearStatus())
    private val _stations = MutableStateFlow<List<WearStation>>(emptyList())
    private val _messages = MutableStateFlow<List<WearMessage>>(emptyList())

    val status:   StateFlow<WearStatus>        = _status
    val stations: StateFlow<List<WearStation>> = _stations
    val messages: StateFlow<List<WearMessage>> = _messages

    val unreadCount get() = _messages.value.count { !it.read }

    // ── Fetch current snapshot from DataClient on startup ─────────────────────

    suspend fun fetchSnapshot(context: Context) {
        try {
            val client = Wearable.getDataClient(context)
            val items  = client.getDataItems().await()
            items.forEach { item ->
                val dmi = DataMapItem.fromDataItem(item)
                when (item.uri.path) {
                    PATH_STATUS   -> parseStatus(dmi.dataMap.getString("json") ?: return@forEach)
                    PATH_STATIONS -> parseStations(dmi.dataMap.getString("json") ?: return@forEach)
                    PATH_MESSAGES -> parseMessages(dmi.dataMap.getString("json") ?: return@forEach)
                }
            }
            items.release()
        } catch (e: Exception) { Log.e(TAG, "fetchSnapshot: $e") }
    }

    // ── Called from WearDataListenerService on push ───────────────────────────

    fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val dmi = DataMapItem.fromDataItem(event.dataItem)
            val json = dmi.dataMap.getString("json") ?: return@forEach
            when (event.dataItem.uri.path) {
                PATH_STATUS   -> parseStatus(json)
                PATH_STATIONS -> parseStations(json)
                PATH_MESSAGES -> parseMessages(json)
            }
        }
    }

    // ── Parsers ───────────────────────────────────────────────────────────────

    private fun parseStatus(json: String) {
        try {
            val o = org.json.JSONObject(json)
            _status.value = WearStatus(
                connected    = o.optBoolean("connected"),
                stationCount = o.optInt("stationCount"),
                lastBeaconTs = o.optLong("lastBeaconTs"),
                myCallsign   = o.optString("myCallsign"),
                myLat        = o.optDouble("myLat"),
                myLon        = o.optDouble("myLon"),
                speedKmh     = o.optDouble("speedKmh"),
                course       = o.optInt("course")
            )
        } catch (e: Exception) { Log.e(TAG, "parseStatus: $e") }
    }

    private fun parseStations(json: String) {
        try {
            val arr = JSONArray(json)
            _stations.value = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                WearStation(
                    callsign    = o.optString("callsign"),
                    lat         = o.optDouble("lat"),
                    lon         = o.optDouble("lon"),
                    comment     = o.optString("comment"),
                    lastHeardMs = o.optLong("lastHeardMs"),
                    distKm      = o.optDouble("distKm")
                )
            }
        } catch (e: Exception) { Log.e(TAG, "parseStations: $e") }
    }

    private fun parseMessages(json: String) {
        try {
            val arr = JSONArray(json)
            _messages.value = (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                WearMessage(
                    from = o.optString("from"),
                    body = o.optString("body"),
                    ts   = o.optLong("ts"),
                    read = o.optBoolean("read", true)
                )
            }
        } catch (e: Exception) { Log.e(TAG, "parseMessages: $e") }
    }

    const val PATH_STATUS   = "/aprs/status"
    const val PATH_STATIONS = "/aprs/stations"
    const val PATH_MESSAGES = "/aprs/messages"
    const val MSG_BEACON    = "/aprs/beacon/now"
    const val MSG_SEND      = "/aprs/message/send"
}

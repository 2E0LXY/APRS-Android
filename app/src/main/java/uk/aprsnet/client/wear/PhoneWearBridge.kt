package uk.aprsnet.client.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uk.aprsnet.client.data.SettingsStore
import uk.aprsnet.client.location.Fix

private const val TAG = "PhoneWearBridge"

private const val PATH_STATUS   = "/aprs/status"
private const val PATH_STATIONS = "/aprs/stations"
private const val PATH_MESSAGES = "/aprs/messages"

/**
 * Pushes APRS state to connected Wear OS devices via the Wearable DataClient.
 * Called from AprsViewModel whenever the relevant data changes.
 * All methods are safe to call on any thread.
 */
object PhoneWearBridge {

    suspend fun pushStatus(
        context: Context,
        connected: Boolean,
        stationCount: Int,
        lastBeaconTs: Long,
        settings: SettingsStore,
        fix: Fix?
    ) = putData(context, PATH_STATUS, JSONObject().apply {
        put("connected",    connected)
        put("stationCount", stationCount)
        put("lastBeaconTs", lastBeaconTs)
        put("myCallsign",   settings.callsign ?: "")
        put("myLat",        fix?.lat ?: 0.0)
        put("myLon",        fix?.lon ?: 0.0)
        put("speedKmh",     fix?.speedKmh ?: 0.0)
        put("course",       fix?.course?.takeIf { it >= 0 } ?: 0)
    }.toString())

    suspend fun pushStations(context: Context, stations: List<uk.aprsnet.client.auto.AutoDataBridge.StationData>, myLat: Double, myLon: Double) {
        val arr = JSONArray()
        stations.take(20).forEach { s ->
            arr.put(JSONObject().apply {
                put("callsign",    s.callsign)
                put("lat",         s.lat)
                put("lon",         s.lon)
                put("comment",     s.comment)
                put("lastHeardMs", s.lastHeardMs)
                val distKm = if (myLat != 0.0 && myLon != 0.0)
                    haversineKm(myLat, myLon, s.lat, s.lon) else 0.0
                put("distKm", distKm)
            })
        }
        putData(context, PATH_STATIONS, arr.toString())
    }

    suspend fun pushMessages(context: Context, messages: List<uk.aprsnet.client.data.MessageEntity>, myCallsign: String) {
        val arr = JSONArray()
        messages
            .filter { it.remoteCall.isNotBlank() }
            .sortedByDescending { it.timestamp }
            .take(10)
            .forEach { m ->
                arr.put(JSONObject().apply {
                    put("from", if (m.outgoing) myCallsign else m.remoteCall)
                    put("body", m.text)
                    put("ts",   m.timestamp)
                    put("read", m.read)
                })
            }
        putData(context, PATH_MESSAGES, arr.toString())
    }

    private suspend fun putData(context: Context, path: String, json: String) =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = PutDataMapRequest.create(path).apply {
                    dataMap.putString("json", json)
                    dataMap.putLong("ts", System.currentTimeMillis()) // force change detection
                }.asPutDataRequest().setUrgent()
                Wearable.getDataClient(context).putDataItem(req)
            }.onFailure { Log.e(TAG, "putData $path: $it") }
        }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r    = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a    = Math.sin(dLat/2).let { it*it } +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2).let { it*it }
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
    }
}

package uk.aprsnet.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import uk.aprsnet.client.aprs.PacketParser
import uk.aprsnet.client.model.Station
import uk.aprsnet.client.model.StationType
import uk.aprsnet.client.net.AprsWebSocket

/**
 * Holds the live application state for Stage 1: the WebSocket connection and
 * the set of stations heard, fed from the rx flows. UI observes the StateFlows.
 */
class AprsViewModel : ViewModel() {

    val ws = AprsWebSocket()

    private val _stations = MutableStateFlow<Map<String, Station>>(emptyMap())
    val stations: StateFlow<Map<String, Station>> = _stations

    val connState: StateFlow<AprsWebSocket.ConnState> = ws.state

    init {
        // Decoded position objects -> Station map
        viewModelScope.launch {
            ws.positionData.collect { json ->
                val call = json.optString("call")
                if (call.isNotEmpty() &&
                    json.has("lat") && json.has("lon")
                ) {
                    val s = Station(
                        callsign = call,
                        lat = json.optDouble("lat"),
                        lon = json.optDouble("lon"),
                        comment = json.optString("raw", ""),
                        path = json.optString("path", ""),
                        raw = json.optString("raw", ""),
                        lastHeard = System.currentTimeMillis(),
                        type = StationType.HAM
                    )
                    _stations.value = _stations.value + (call to s)
                }
            }
        }
        // Raw packets -> parse for anything the decoded feed missed
        viewModelScope.launch {
            ws.rawPackets.collect { raw ->
                when (val p = PacketParser.parse(raw)) {
                    is PacketParser.Parsed.Pos -> {
                        _stations.value =
                            _stations.value + (p.station.callsign to p.station)
                    }
                    else -> { /* messages handled in Stage 2 */ }
                }
            }
        }
    }

    fun start(callsign: String, passcode: String) {
        if (callsign.isNotEmpty()) ws.setCredentials(callsign, passcode)
        ws.connect()
    }

    override fun onCleared() {
        ws.disconnect()
        super.onCleared()
    }
}
package uk.aprsnet.client.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import uk.aprsnet.client.auto.AutoDataBridge

private const val TAG = "WearListenerService"
private const val MSG_BEACON = "/aprs/beacon/now"
private const val MSG_SEND   = "/aprs/message/send"

/**
 * Receives messages from the Wear OS app and dispatches them to the phone app.
 * Runs as a bound service — Android starts it on demand.
 */
class WearListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "onMessageReceived: ${event.path}")
        CoroutineScope(Dispatchers.Main).launch {
            when (event.path) {
                MSG_BEACON -> {
                    AutoDataBridge.onBeaconNow?.invoke()
                    Log.i(TAG, "Beacon triggered from watch")
                }
                MSG_SEND -> {
                    val json = String(event.data)
                    val obj  = JSONObject(json)
                    val to   = obj.optString("to")
                    val body = obj.optString("body")
                    if (to.isNotBlank() && body.isNotBlank()) {
                        AutoDataBridge.onSendMessage?.invoke(to, body)
                        Log.i(TAG, "Message sent from watch to $to: $body")
                    }
                }
            }
        }
    }
}

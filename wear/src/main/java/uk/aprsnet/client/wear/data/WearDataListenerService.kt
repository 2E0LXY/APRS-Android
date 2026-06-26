package uk.aprsnet.client.wear.data

import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

/**
 * Receives DataClient pushes from the phone app and forwards them to
 * WearDataBridge so the Compose UI re-renders automatically.
 */
class WearDataListenerService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        WearDataBridge.onDataChanged(events)
        events.release()
    }
}

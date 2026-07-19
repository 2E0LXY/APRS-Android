package uk.aprsnet.client.net

import java.util.IdentityHashMap

/**
 * One APRS WebSocket per Android process.
 *
 * The foreground service and the activity ViewModel previously created
 * independent sockets, so every running phone appeared twice in the server's
 * Connected Clients table. Owners are tracked by identity: the socket stays
 * alive while either the UI or service needs it and closes only after the last
 * owner is released.
 */
object AprsConnectionManager {
    val socket = AprsWebSocket()

    private val owners = IdentityHashMap<Any, Boolean>()

    @Synchronized
    fun acquire(owner: Any) {
        owners[owner] = true
        socket.connect()
    }

    @Synchronized
    fun release(owner: Any) {
        owners.remove(owner)
        if (owners.isEmpty()) socket.disconnect()
    }
}

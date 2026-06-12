package uk.aprsnet.client.data

import kotlinx.coroutines.flow.Flow
import uk.aprsnet.client.aprs.PacketBuilder
import uk.aprsnet.client.aprs.PacketParser
import uk.aprsnet.client.model.MessageState
import uk.aprsnet.client.net.AprsWebSocket

/**
 * Messaging logic. Owns sending, receiving, ACK matching (the green-bubble
 * behaviour), auto-ACK of incoming messages, and retry of un-ACKed messages.
 */
class MessageRepository(
    private val dao: MessageDao,
    private val ws: AprsWebSocket
) {
    /** Set by the ViewModel once the user has configured a callsign. */
    @Volatile var myCallsign: String = ""

    private var msgIdCounter = 1

    fun conversations(): Flow<List<ConversationSummary>> = dao.conversations()
    fun thread(call: String): Flow<List<MessageEntity>> = dao.thread(call)
    fun totalUnread(): Flow<Int> = dao.totalUnread()

    suspend fun markRead(call: String) = dao.markRead(call)

    suspend fun deleteConversation(call: String) = dao.deleteConversation(call)

    /** Next cycling 2-digit APRS message id. */
    private fun nextMsgId(): String {
        val id = msgIdCounter % 100
        msgIdCounter++
        return id.toString().padStart(2, '0')
    }

    /**
     * Send a text message. Stores it, transmits it, and returns the row id.
     * State starts SENDING -> SENT once transmitted; flips to ACKED when the
     * matching ackNN arrives (see handleIncoming).
     */
    suspend fun sendMessage(to: String, text: String): Long {
        val dest = to.trim().uppercase()
        val msgId = nextMsgId()
        val rowId = dao.insert(
            MessageEntity(
                remoteCall = dest,
                text = text,
                outgoing = true,
                timestamp = System.currentTimeMillis(),
                aprsMsgId = msgId,
                state = MessageState.SENDING.name,
                read = true
            )
        )
        val packet = PacketBuilder.message(myCallsign, dest, text, msgId)
        val sent = ws.transmit(packet)
        dao.byId(rowId)?.let {
            dao.update(it.copy(state = if (sent) MessageState.SENT.name
                                       else MessageState.FAILED.name))
        }
        return rowId
    }

    /** Re-transmit a message that has not been ACKed. */
    suspend fun retry(rowId: Long) {
        val m = dao.byId(rowId) ?: return
        if (m.aprsMsgId == null) return
        val packet = PacketBuilder.message(myCallsign, m.remoteCall, m.text, m.aprsMsgId)
        val sent = ws.transmit(packet)
        dao.update(m.copy(
            state = if (sent) MessageState.SENT.name else MessageState.FAILED.name,
            retries = m.retries + 1
        ))
    }

    /**
     * Handle a raw incoming packet. If it's a message addressed to us, store
     * it and auto-ACK. If it's an ACK for one of our messages, flip that
     * message to ACKED so the bubble turns green.
     * Returns a stored incoming MessageEntity if one was created (for the
     * caller to raise a notification), else null.
     */
    suspend fun handleIncoming(raw: String): MessageEntity? {
        val parsed = PacketParser.parse(raw)
        if (parsed !is PacketParser.Parsed.Msg) return null

        val me = myCallsign.uppercase()

        // --- an ACK for one of our outgoing messages ---
        if (parsed.isAck && parsed.ackId != null) {
            val outgoing = dao.findOutgoing(parsed.from, parsed.ackId)
            if (outgoing != null && outgoing.stateEnum() != MessageState.ACKED) {
                dao.update(outgoing.copy(state = MessageState.ACKED.name))
            }
            return null
        }

        // --- a normal message; only handle those addressed to us ---
        if (me.isEmpty()) return null
        val to = parsed.to.uppercase()
        val meBase = me.substringBefore("-")
        // Accept messages addressed to either our full callsign-with-SSID or
        // the bare base callsign - APRS clients use both conventions.
        if (to != me && to != meBase) return null

        val entity = MessageEntity(
            remoteCall = parsed.from,
            text = parsed.text,
            outgoing = false,
            timestamp = System.currentTimeMillis(),
            aprsMsgId = parsed.msgId,
            state = MessageState.SENT.name,
            read = false
        )
        val rowId = dao.insert(entity)

        // auto-ACK if the sender included a message id
        if (parsed.msgId != null) {
            ws.transmit(PacketBuilder.ack(myCallsign, parsed.from, parsed.msgId))
        }
        return entity.copy(id = rowId)
    }

    /**
     * Send a new message directly via the APRS Net server, bypassing APRS-IS
     * entirely. Used when the user explicitly chooses "Direct" delivery at
     * compose time. Recipient must be a registered member.
     * State starts SENDING -> SERVER_SENT on success, FAILED on error.
     */
    suspend fun sendDirectMessage(to: String, text: String, token: String): Long {
        val dest = to.trim().uppercase()
        val rowId = dao.insert(
            MessageEntity(
                remoteCall = dest,
                text       = text,
                outgoing   = true,
                timestamp  = System.currentTimeMillis(),
                aprsMsgId  = null,   // direct delivery — no APRS message id
                state      = MessageState.SENDING.name,
                read       = true
            )
        )
        val sent = uk.aprsnet.client.net.AprsApi.memberMessageSend(token, dest, text)
        dao.byId(rowId)?.let {
            dao.update(it.copy(
                state = if (sent) MessageState.SERVER_SENT.name
                        else     MessageState.FAILED.name
            ))
        }
        return rowId
    }

    /**
     * Attempt to deliver a FAILED message directly via the APRS Net server,
     * bypassing APRS-IS. Only valid when the recipient is a registered member.
     * Updates the message state to SERVER_SENT on success, leaves it FAILED
     * on error so the caller can show a user-visible error.
     */
    suspend fun sendViaServer(rowId: Long, token: String): Boolean {
        val m = dao.byId(rowId) ?: return false
        val sent = uk.aprsnet.client.net.AprsApi.memberMessageSend(token, m.remoteCall, m.text)
        if (sent) {
            dao.update(m.copy(state = uk.aprsnet.client.model.MessageState.SERVER_SENT.name))
        }
        return sent
    }

    /** Retry sweep - resend SENT messages that never got an ACK. */
    suspend fun retrySweep() {
        dao.pendingAcks().forEach { m ->
            val age = System.currentTimeMillis() - m.timestamp
            // resend if older than 30s * (retries+1)
            if (age > 30_000L * (m.retries + 1)) {
                if (m.retries >= 2) {
                    dao.update(m.copy(state = MessageState.FAILED.name))
                } else {
                    retry(m.id)
                }
            }
        }
    }

    /**
     * Merge the server-side message history into the local Room database.
     * Called after login and on reconnect so messages from other devices
     * (web, desktop, iOS) become visible in the app.
     *
     * Deduplication: skips entries whose serverMsgId is already present for
     * the same remote callsign.
     */
    suspend fun syncFromServer(serverMessages: org.json.JSONArray) {
        val me = myCallsign.uppercase()
        for (i in 0 until serverMessages.length()) {
            val obj = serverMessages.optJSONObject(i) ?: continue
            val from = obj.optString("from").uppercase()
            val to   = obj.optString("to").uppercase()
            val text = obj.optString("text")
            val ts   = obj.optLong("ts", 0L) * 1000L // server stores seconds
            val dir  = obj.optString("direction", "in")   // "in" | "out"
            val msgId = obj.optString("id").takeIf { it.isNotEmpty() }

            val outgoing = (dir == "out") ||
                           (from.substringBefore("-") == me.substringBefore("-"))
            val remote = if (outgoing) to else from

            if (msgId != null && dao.findByServerId(msgId, remote) != null) continue

            dao.insertIfAbsent(
                MessageEntity(
                    remoteCall  = remote,
                    text        = text,
                    outgoing    = outgoing,
                    timestamp   = if (ts > 0L) ts else System.currentTimeMillis(),
                    aprsMsgId   = null,
                    serverMsgId = msgId,
                    state       = MessageState.SENT.name,
                    read        = outgoing || obj.optBoolean("read", false)
                )
            )
        }
    }
}
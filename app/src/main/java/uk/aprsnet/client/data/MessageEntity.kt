package uk.aprsnet.client.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import uk.aprsnet.client.model.MessageState

/**
 * A stored APRS message - one row per message, grouped into conversations
 * by remoteCall. Persists so threads survive app restarts.
 */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["serverMsgId", "remoteCall"], unique = true, name = "idx_server_msg")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteCall: String,        // the other party's callsign
    val text: String,
    val outgoing: Boolean,
    val timestamp: Long,
    val aprsMsgId: String?,        // the {NN id, for ACK matching
    val state: String,             // MessageState name
    val read: Boolean = false,     // for unread badges
    val retries: Int = 0,
    val serverMsgId: String? = null  // UUID from server — sync deduplication
) {
    fun stateEnum(): MessageState =
        runCatching { MessageState.valueOf(state) }.getOrDefault(MessageState.SENT)
}
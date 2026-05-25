package uk.aprsnet.client.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A conversation summary row for the conversation list. */
data class ConversationSummary(
    val remoteCall: String,
    val lastText: String,
    val lastTimestamp: Long,
    val unread: Int
)

@Dao
interface MessageDao {

    @Insert
    suspend fun insert(msg: MessageEntity): Long

    @Update
    suspend fun update(msg: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: Long): MessageEntity?

    /** All messages in one conversation, oldest first. */
    @Query("SELECT * FROM messages WHERE remoteCall = :call ORDER BY timestamp ASC")
    fun thread(call: String): Flow<List<MessageEntity>>

    /** Conversation list: latest message per callsign + unread count. */
    @Query(
        """
        SELECT m.remoteCall AS remoteCall,
               m.text AS lastText,
               m.timestamp AS lastTimestamp,
               (SELECT COUNT(*) FROM messages u
                 WHERE u.remoteCall = m.remoteCall
                   AND u.outgoing = 0 AND u.read = 0) AS unread
        FROM messages m
        INNER JOIN (
            SELECT remoteCall, MAX(timestamp) AS mx
            FROM messages GROUP BY remoteCall
        ) latest
        ON m.remoteCall = latest.remoteCall AND m.timestamp = latest.mx
        ORDER BY m.timestamp DESC
        """
    )
    fun conversations(): Flow<List<ConversationSummary>>

    /** Find an outgoing message awaiting ACK by its APRS id + callsign. */
    @Query(
        """SELECT * FROM messages
           WHERE remoteCall = :call AND aprsMsgId = :msgId
             AND outgoing = 1 ORDER BY timestamp DESC LIMIT 1"""
    )
    suspend fun findOutgoing(call: String, msgId: String): MessageEntity?

    @Query("UPDATE messages SET read = 1 WHERE remoteCall = :call AND outgoing = 0")
    suspend fun markRead(call: String)

    @Query("SELECT COUNT(*) FROM messages WHERE outgoing = 0 AND read = 0")
    fun totalUnread(): Flow<Int>

    /** Outgoing messages still awaiting an ACK (for retry sweeps). */
    @Query(
        """SELECT * FROM messages
           WHERE outgoing = 1 AND state = 'SENT' AND retries < 3"""
    )
    suspend fun pendingAcks(): List<MessageEntity>
}
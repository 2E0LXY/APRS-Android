package uk.aprsnet.client.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved contact - a callsign the user wants to keep, optionally with a
 * friendly alias shown in message threads and notifications.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val callsign: String,
    val alias: String = "",
    val notes: String = "",
    val addedAt: Long = System.currentTimeMillis()
)
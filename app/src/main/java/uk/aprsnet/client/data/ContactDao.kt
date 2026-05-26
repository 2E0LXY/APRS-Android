package uk.aprsnet.client.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Update
    suspend fun update(contact: ContactEntity)

    @Delete
    suspend fun delete(contact: ContactEntity)

    @Query("SELECT * FROM contacts ORDER BY callsign ASC")
    fun all(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE callsign = :call LIMIT 1")
    suspend fun byCallsign(call: String): ContactEntity?

    @Query("SELECT alias FROM contacts WHERE callsign = :call LIMIT 1")
    suspend fun aliasFor(call: String): String?
}
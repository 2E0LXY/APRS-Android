package uk.aprsnet.client.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MessageEntity::class, ContactEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // Adds a unique index on (serverMsgId, remoteCall) so that
        // insertIfAbsent()'s OnConflictStrategy.IGNORE actually fires and
        // prevents duplicate rows during server message sync.
        // Rows with null serverMsgId are excluded from the uniqueness constraint
        // by SQLite's standard behaviour (NULLs are not considered equal).
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE UNIQUE INDEX IF NOT EXISTS idx_server_msg
                       ON messages(serverMsgId, remoteCall)
                       WHERE serverMsgId IS NOT NULL"""
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aprsnet.db"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
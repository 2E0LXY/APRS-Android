package uk.aprsnet.client.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import uk.aprsnet.client.data.AppDatabase

/**
 * One Session per head unit connection.
 * Creates the root screen (conversation list) and hands down the DB instance.
 */
class AprsCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val db = AppDatabase.get(carContext)
        return ConversationListCarScreen(carContext, db)
    }
}

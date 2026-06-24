package uk.aprsnet.client.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import uk.aprsnet.client.auto.screens.HomeScreen

/**
 * One Session per head unit connection.
 * Routes to the full APRS Net Auto UI (v2.9.0 — navigation category).
 */
class AprsCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = HomeScreen(carContext)
}

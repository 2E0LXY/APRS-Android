package uk.aprsnet.client.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import kotlinx.coroutines.launch
import uk.aprsnet.client.wear.data.WearDataBridge
import uk.aprsnet.client.wear.screens.BeaconScreen
import uk.aprsnet.client.wear.screens.HomeScreen
import uk.aprsnet.client.wear.screens.MessagesScreen
import uk.aprsnet.client.wear.screens.StationsScreen
import uk.aprsnet.client.wear.theme.WearAppTheme

class WearMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fetch initial data snapshot from phone
        lifecycleScope.launch { WearDataBridge.fetchSnapshot(applicationContext) }

        setContent {
            WearAppTheme {
                val navController: NavHostController = rememberSwipeDismissableNavController()
                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = "home"
                ) {
                    composable("home")     { HomeScreen(navController) }
                    composable("stations") { StationsScreen(navController) }
                    composable("messages") { MessagesScreen(navController) }
                    composable("beacon")   { BeaconScreen(navController) }
                }
            }
        }
    }
}

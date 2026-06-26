package uk.aprsnet.client.wear.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme

@Composable
fun WearAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

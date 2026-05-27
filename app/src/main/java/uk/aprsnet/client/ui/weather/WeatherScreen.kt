package uk.aprsnet.client.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.model.StationType
import uk.aprsnet.client.net.AprsApi
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.BgPanel
import uk.aprsnet.client.ui.theme.Err
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim

/** Weather: Met Office severe-weather warnings + heard CWOP weather stations. */
@Composable
fun WeatherScreen(vm: AprsViewModel, modifier: Modifier = Modifier) {
    val stations by vm.stations.collectAsState()
    val warnings by produceState(initialValue = emptyList<String>()) {
        value = AprsApi.weatherWarnings()
    }
    val wxStations = stations.values.filter { it.type == StationType.WEATHER }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Card("UK Severe Weather Warnings") {
            if (warnings.isEmpty()) {
                Text("No active warnings", color = TextDim, fontSize = 13.sp)
            } else {
                warnings.forEach { w ->
                    Text(
                        "- $w",
                        color = Err, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
        Card("Weather Stations (${wxStations.size})") {
            if (wxStations.isEmpty()) {
                Text("No weather stations heard yet", color = TextDim, fontSize = 13.sp)
            } else {
                wxStations.take(40).forEach { st ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(st.callsign, color = Accent, fontWeight = FontWeight.Bold,
                            fontSize = 14.sp)
                        if (st.comment.isNotEmpty()) {
                            Text(st.comment, color = TextDim, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgPanel)
            .padding(14.dp)
    ) {
        Text(title.uppercase(), color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.size(8.dp))
        content()
    }
}
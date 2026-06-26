package uk.aprsnet.client.wear.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import uk.aprsnet.client.wear.data.WearDataBridge
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessagesScreen(nav: NavController) {
    val messages by WearDataBridge.messages.collectAsState()
    val fmt = SimpleDateFormat("HH:mm", Locale.UK)

    ScalingLazyColumn(
        modifier            = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding      = PaddingValues(vertical = 24.dp)
    ) {
        item {
            Text("Messages", style = MaterialTheme.typography.title3)
        }

        if (messages.isEmpty()) {
            item { Text("No messages", color = Color.Gray, fontSize = 12.sp) }
        }

        items(messages.take(8)) { msg ->
            TitleCard(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 2.dp),
                onClick  = {},
                title    = {
                    Text(
                        msg.from,
                        fontSize = 13.sp,
                        color    = if (!msg.read) MaterialTheme.colors.primary else Color.White
                    )
                },
                time     = { Text(fmt.format(Date(msg.ts)), fontSize = 10.sp) }
            ) {
                Text(
                    msg.body,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Quick replies
        val quickReplies = listOf("73", "QSL", "On my way", "Standing by")
        item { Spacer(Modifier.height(8.dp)) }
        item { Text("Quick reply to last:", fontSize = 10.sp, color = Color.Gray) }
        items(quickReplies) { reply ->
            val lastFrom = messages.firstOrNull()?.from ?: return@items
            val context  = androidx.compose.ui.platform.LocalContext.current
            CompactChip(
                modifier = Modifier.fillMaxWidth(0.85f),
                onClick  = {
                    CoroutineScope(Dispatchers.IO).launch {
                        val payload = JSONObject().apply {
                            put("to",   lastFrom)
                            put("body", reply)
                        }.toString().toByteArray()
                        Wearable.getMessageClient(context)
                            .sendMessage("", WearDataBridge.MSG_SEND, payload).await()
                    }
                },
                label    = { Text(reply, fontSize = 11.sp) }
            )
        }
    }
}

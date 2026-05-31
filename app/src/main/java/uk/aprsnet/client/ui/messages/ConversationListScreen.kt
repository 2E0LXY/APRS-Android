package uk.aprsnet.client.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.AccentBlue
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Conversation list - one row per remote callsign, newest first.
 * FAB opens a "new message to..." dialog so a conversation can be started
 * without first having to find the station on the map.
 */
@Composable
fun ConversationListScreen(
    vm: AprsViewModel,
    onOpenThread: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val conversations by vm.conversations.collectAsState(initial = emptyList())
    var showNew by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No messages yet - tap + to start one",
                    color = TextDim, fontSize = 13.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(conversations) { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenThread(c.remoteCall) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.remoteCall,
                                color = Accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                c.lastText.ifEmpty { "(no text)" },
                                color = TextDim,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(
                            relTime(c.lastTimestamp),
                            color = TextDim,
                            fontSize = 11.sp
                        )
                        if (c.unread > 0) {
                            Spacer(Modifier.size(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(AccentBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    c.unread.toString(),
                                    color = TextBase,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showNew = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = "New message")
        }
    }

    if (showNew) {
        var to by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New message") },
            text = {
                OutlinedTextField(
                    value = to,
                    onValueChange = { to = it.uppercase() },
                    label = { Text("To callsign") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = to.trim()
                    showNew = false
                    if (t.isNotEmpty()) onOpenThread(t)
                }) { Text("Open") }
            },
            dismissButton = {
                TextButton(onClick = { showNew = false }) { Text("Cancel") }
            }
        )
    }
}

private fun relTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        else -> SimpleDateFormat("d MMM", Locale.UK).format(Date(ts))
    }
}
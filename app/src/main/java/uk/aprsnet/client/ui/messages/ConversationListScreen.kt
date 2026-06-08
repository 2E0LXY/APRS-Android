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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.BgDeep
import uk.aprsnet.client.ui.theme.AccentBlue
import uk.aprsnet.client.ui.theme.AccentRose
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim
import uk.aprsnet.client.ui.theme.TextMute
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Conversation list, Google-Messages-style.
 *  - large coloured avatar circle (per-callsign tint derived from a hash)
 *  - bold callsign + muted last-message preview
 *  - smart timestamp on the right (today=HH:mm, this week=day, older=date)
 *  - small unread dot
 *  - swipe LEFT to delete (with confirm dialog)
 *  - FAB to compose to a new callsign
 *
 * Archive (swipe right) is queued for a follow-up release - it needs a new
 * column on the conversation table.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    vm: AprsViewModel,
    onOpenThread: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val conversations by vm.conversations.collectAsState(initial = emptyList())
    val members by vm.memberCallsigns.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
      uk.aprsnet.client.ui.common.MessageBackground(backgroundId = vm.settings.messageBackgroundId) {
        if (conversations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No messages yet - tap + to start a conversation",
                    color = TextDim, fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(conversations, key = { it.remoteCall }) { c ->
                    val state = rememberSwipeToDismissBoxState(
                        confirmValueChange = { v ->
                            if (v == SwipeToDismissBoxValue.EndToStart) {
                                pendingDelete = c.remoteCall
                                false        // wait for confirm dialog
                            } else false
                        }
                    )
                    SwipeToDismissBox(
                        state = state,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(AccentRose)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        }
                    ) {
                        // Opaque page-background under the row so the rose-red delete
                        // background does not bleed through the row text and timestamp
                        // while the swipe is in progress.
                        Box(modifier = Modifier.background(BgDeep)) {
                          ConversationRow(
                            callsign = c.remoteCall,
                            preview = c.lastText.ifEmpty { "(no text)" },
                            timestamp = c.lastTimestamp,
                            unread = c.unread,
                            isMember = c.remoteCall.uppercase() in members,
                            onClick = { onOpenThread(c.remoteCall) }
                        )
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
      }  // closes MessageBackground wrap
    }

    // delete confirm
    pendingDelete?.let { call ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete conversation") },
            text = { Text("Delete the entire message history with $call? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteConversation(call)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    // compose-to
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

@Composable
private fun ConversationRow(
    callsign: String,
    preview: String,
    timestamp: Long,
    unread: Int,
    isMember: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(callsign)
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    callsign,
                    color = TextBase,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                if (isMember) {
                    Spacer(Modifier.size(5.dp))
                    uk.aprsnet.client.ui.common.AnukBadge()
                }
            }
            Spacer(Modifier.size(2.dp))
            Text(
                uk.aprsnet.client.util.Emoji.render(preview),
                color = if (unread > 0) TextBase else TextDim,
                fontSize = 13.sp,
                maxLines = 1
            )
        }
        Spacer(Modifier.size(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                smartTime(timestamp),
                color = if (unread > 0) Accent else TextMute,
                fontSize = 11.sp,
                fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal
            )
            if (unread > 0) {
                Spacer(Modifier.size(4.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(AccentBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (unread > 9) "9+" else unread.toString(),
                        color = TextBase, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Round, gradient-filled initials avatar. Tint hashed from the callsign. */
@Composable
private fun Avatar(callsign: String) {
    val palette = listOf(
        Color(0xFF06B6D4) to Color(0xFF3B82F6),     // cyan -> blue
        Color(0xFFA855F7) to Color(0xFFEC4899),     // purple -> magenta
        Color(0xFF84CC16) to Color(0xFF22C55E),     // lime -> green
        Color(0xFFF59E0B) to Color(0xFFEF4444),     // amber -> red
        Color(0xFF38BDF8) to Color(0xFF818CF8),     // sky -> indigo
        Color(0xFFEC4899) to Color(0xFFF43F5E)      // pink -> rose
    )
    val idx = (callsign.hashCode().rem(palette.size) + palette.size) % palette.size
    val (a, b) = palette[idx]
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center
    ) {
        val initials = callsign.filter(Char::isLetterOrDigit).take(2).uppercase()
        Text(
            initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

private fun smartTime(ts: Long): String {
    if (ts <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    return when {
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) ->
            SimpleDateFormat("HH:mm", Locale.UK).format(Date(ts))
        now.timeInMillis - ts < 7L * 86_400_000L ->
            SimpleDateFormat("EEE", Locale.UK).format(Date(ts))
        else ->
            SimpleDateFormat("d MMM", Locale.UK).format(Date(ts))
    }
}

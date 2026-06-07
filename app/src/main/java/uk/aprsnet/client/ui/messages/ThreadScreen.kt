package uk.aprsnet.client.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Refresh
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
import uk.aprsnet.client.data.MessageEntity
import uk.aprsnet.client.model.MessageState
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.AccentBlue
import uk.aprsnet.client.ui.theme.AccentLime
import uk.aprsnet.client.ui.theme.BubbleAcked
import uk.aprsnet.client.ui.theme.BubbleMine
import uk.aprsnet.client.ui.theme.BubbleThem
import uk.aprsnet.client.ui.theme.Err
import uk.aprsnet.client.ui.theme.TextBase
import uk.aprsnet.client.ui.theme.TextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A conversation thread. Chat bubbles:
 *   outgoing = right, blue   -> turns GREEN when ACKed (double tick)
 *   incoming = left, grey
 * Compose bar with a 67-char APRS limit.
 */
@Composable
fun ThreadScreen(
    vm: AprsViewModel,
    callsign: String,
    modifier: Modifier = Modifier
) {
    val messages by vm.thread(callsign).collectAsState(initial = emptyList())
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // mark incoming messages read on open
    LaunchedEffect(callsign) { vm.markRead(callsign) }
    // keep the newest message in view
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)
        ) {
            items(messages) { m -> MessageBubble(m, vm.settings.bubbleColourId, vm.settings.incomingBubbleColourId) { vm.retry(m.id) } }
        }

        // compose bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 67) draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message (max 67)") },
                singleLine = false,
                maxLines = 3
            )
            Spacer(Modifier.size(6.dp))
            IconButton(
                onClick = {
                    val t = draft.trim()
                    if (t.isNotEmpty()) { vm.send(callsign, t); draft = "" }
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send", tint = Accent)
            }
        }
        Text(
            "${draft.length} / 67",
            color = TextDim,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 16.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun MessageBubble(m: MessageEntity, bubbleColourId: Int, incomingBubbleColourId: Int, onRetry: () -> Unit) {
    val state = m.stateEnum()
    val align = if (m.outgoing) Alignment.End else Alignment.Start

    val shape = if (m.outgoing)
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    else
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

    // Both directions render as gradients. ACKed outgoing -> lime/green;
    // failed outgoing -> red. Incoming uses the user-picked palette.
    val gradient = when {
        m.outgoing && state == MessageState.ACKED -> Brush.verticalGradient(
            listOf(AccentLime, BubbleAcked))
        m.outgoing && state == MessageState.FAILED -> Brush.verticalGradient(
            listOf(Err, Err))
        m.outgoing -> {
            val palette = uk.aprsnet.client.ui.theme.BUBBLE_PALETTES
                .getOrNull(bubbleColourId)
                ?: uk.aprsnet.client.ui.theme.BUBBLE_PALETTES[0]
            Brush.verticalGradient(listOf(palette.top, palette.bottom))
        }
        else -> {
            val palette = uk.aprsnet.client.ui.theme.BUBBLE_PALETTES
                .getOrNull(incomingBubbleColourId)
                ?: uk.aprsnet.client.ui.theme.BUBBLE_PALETTES[0]
            Brush.verticalGradient(listOf(palette.top, palette.bottom))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalAlignment = align
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(gradient)
                .clickable(enabled = m.outgoing && state == MessageState.FAILED) { onRetry() }
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Column {
                Text(uk.aprsnet.client.util.Emoji.render(m.text), color = TextBase, fontSize = 14.sp)
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        SimpleDateFormat("HH:mm", Locale.UK).format(Date(m.timestamp)),
                        color = TextDim, fontSize = 9.sp
                    )
                    if (m.outgoing) {
                        Spacer(Modifier.size(4.dp))
                        Text(tickFor(state), color = tickColor(state), fontSize = 9.sp)
                    }
                }
            }
        }
        if (m.outgoing && state == MessageState.FAILED) {
            Text("Failed - tap to retry", color = Err, fontSize = 10.sp)
        }
    }
}

private fun tickFor(s: MessageState): String = when (s) {
    MessageState.SENDING -> "clock"
    MessageState.SENT    -> "sent"
    MessageState.ACKED   -> "delivered"
    MessageState.FAILED  -> "failed"
}

private fun tickColor(s: MessageState): Color = when (s) {
    MessageState.ACKED  -> Color(0xFF86EFAC)
    MessageState.FAILED -> Err
    else -> TextDim
}
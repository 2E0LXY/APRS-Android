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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.aprs.KNOWN_NETS
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    vm: AprsViewModel,
    callsign: String,
    modifier: Modifier = Modifier
) {
    val messages by vm.thread(callsign).collectAsState(initial = emptyList())
    val members by vm.memberCallsigns.collectAsState()
    var draft by remember { mutableStateOf("") }
    val recipientIsMember = callsign.uppercase() in members
    val userIsSignedIn    = vm.settings.memberSignedIn
    val canUseDirect      = recipientIsMember && userIsSignedIn
    var useDirect by remember(callsign) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Nets bottom sheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showNetsSheet by remember { mutableStateOf(false) }

    // Unjoin prompt: set to the ANSRVR group name (e.g. "HOTG") when we
    // detect that the user just sent a CQ to ANSRVR and received an ACK.
    var pendingUnjoin by remember(callsign) { mutableStateOf<String?>(null) }

    LaunchedEffect(callsign) { vm.markRead(callsign) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Detect ANSRVR ACK for unjoin prompt.
    // When the latest outgoing message was a CQ <GROUP> to ANSRVR and a new
    // incoming message just arrived from ANSRVR (the ACK), offer one-tap unjoin.
    LaunchedEffect(messages.size) {
        if (callsign.uppercase() != "ANSRVR") return@LaunchedEffect
        val lastOut = messages.lastOrNull { it.outgoing } ?: return@LaunchedEffect
        val lastIn  = messages.lastOrNull { !it.outgoing } ?: return@LaunchedEffect
        // Only trigger if the ACK arrived after our outgoing message
        if (lastIn.timestamp <= lastOut.timestamp) return@LaunchedEffect
        // Extract group name from "CQ GROUPNAME ..." pattern
        val cqMatch = Regex("""^CQ\s+(\S+)""", RegexOption.IGNORE_CASE)
            .find(lastOut.text.trim())
        if (cqMatch != null && pendingUnjoin == null) {
            pendingUnjoin = cqMatch.groupValues[1].uppercase()
        }
    }

    uk.aprsnet.client.ui.common.MessageBackground(
        backgroundId = vm.settings.messageBackgroundId,
        modifier = modifier.fillMaxSize()
    ) {
      Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp)
        ) {
            items(messages) { m -> MessageBubble(
                m = m,
                bubbleColourId = vm.settings.bubbleColourId,
                incomingBubbleColourId = vm.settings.incomingBubbleColourId,
                isMember = m.remoteCall.uppercase() in members,
                isMemberSignedIn = vm.settings.memberSignedIn,
                onRetry = { vm.retry(m.id) },
                onSendViaServer = { vm.sendViaServer(m.id) }
            ) }
        }

        // ── Unjoin banner ──────────────────────────────────────────────
        pendingUnjoin?.let { group ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A2A1A))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "✅ Checked in to $group — unjoin when done?",
                    color = Color(0xFF86EFAC), fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF166534))
                        .clickable {
                            vm.send("ANSRVR", "U $group")
                            pendingUnjoin = null
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) { Text("U $group", color = Color(0xFF86EFAC), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.size(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { pendingUnjoin = null }
                        .padding(4.dp)
                ) { Text("✕", color = TextDim, fontSize = 11.sp) }
            }
        }

        // ── Route selector ─────────────────────────────────────────────
        if (canUseDirect) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Route:", color = TextDim, fontSize = 11.sp)
                DeliveryChip(label = "📡 APRS", selected = !useDirect, onClick = { useDirect = false })
                DeliveryChip(label = "↗ Direct", selected = useDirect, onClick = { useDirect = true }, activeColour = Color(0xFF0D9488))
                if (useDirect) Text("no RF", color = TextDim, fontSize = 10.sp)
            }
        }

        // ── Compose row ────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nets button — only meaningful when in ANSRVR / net conversations
            // but shown in all threads as a quick-compose shortcut
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D2137))
                    .clickable { showNetsSheet = true }
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) { Text("📡", fontSize = 16.sp) }
            Spacer(Modifier.size(6.dp))
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
                    if (t.isNotEmpty()) {
                        if (useDirect) vm.sendDirect(callsign, t)
                        else           vm.send(callsign, t)
                        draft = ""
                    }
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send",
                    tint = if (useDirect) Color(0xFF0D9488) else Accent)
            }
        }
        Text("${draft.length} / 67", color = TextDim, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.End).padding(end = 16.dp, bottom = 4.dp))
      }
    }

    // ── Nets bottom sheet ──────────────────────────────────────────────────
    if (showNetsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNetsSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF0B1526)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📡 Net Quick Check-in",
                    color = Accent, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 4.dp))
                Text("Tap a net to pre-fill the compose bar. Add your message then send.",
                    color = TextDim, fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 16.dp))
                KNOWN_NETS.forEach { net ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0D2137))
                            .clickable {
                                // Navigate to the correct conversation if needed,
                                // then pre-fill the draft with the body prefix.
                                // Since we can't navigate from here, we pre-fill
                                // the draft (user may already be in the right thread;
                                // if not, the ConversationList will open via main nav).
                                draft = net.bodyPrefix
                                scope.launch { sheetState.hide() }
                                showNetsSheet = false
                            }
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(net.name, color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    modifier = Modifier.weight(1f))
                                Text("→ ${net.destination}", color = Accent,
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(net.schedule, color = TextDim, fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp))
                            Text("\"${net.bodyPrefix}…\"",
                                color = Color(0xFF86EFAC), fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp))
                            if (net.ansrvrGroup != null) {
                                Text("Auto-unjoin prompt after ACK",
                                    color = TextDim, fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun DeliveryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    activeColour: Color = AccentBlue
) {
    val bg     = if (selected) activeColour.copy(alpha = 0.18f) else Color.Transparent
    val border = if (selected) activeColour else TextDim.copy(alpha = 0.35f)
    val fg     = if (selected) activeColour else TextDim
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun MessageBubble(
    m: MessageEntity,
    bubbleColourId: Int,
    incomingBubbleColourId: Int,
    isMember: Boolean = false,
    isMemberSignedIn: Boolean = false,
    onRetry: () -> Unit,
    onSendViaServer: () -> Unit = {}
) {
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
        m.outgoing && state == MessageState.SERVER_SENT -> Brush.verticalGradient(
            listOf(Color(0xFFF59E0B), Color(0xFFD97706)))
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
        // Retry counter: shows attempt n/3 while waiting for ACK
        if (m.outgoing && state == MessageState.SENT && m.retries > 0) {
            Text(
                retryCounterText(m.retries),
                color = TextDim.copy(alpha = 0.7f),
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        // Failed footer + server fallback
        if (m.outgoing && state == MessageState.FAILED) {
            Text("Failed - tap to retry", color = Err, fontSize = 10.sp)
            // Offer server delivery if recipient is an APRS Net member
            // and we are signed in to a member account
            if (isMember && isMemberSignedIn) {
                Button(
                    onClick = onSendViaServer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0D9488).copy(alpha = 0.9f)
                    ),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text("↗ Send via APRS Net", fontSize = 11.sp)
                }
            }
        }
        if (m.outgoing && state == MessageState.SERVER_SENT) {
            Text("↗ Delivered via APRS Net",
                color = Color(0xFFF59E0B), fontSize = 10.sp)
        }
    }
}

/** Returns Unicode superscript fraction for the retry attempt. */
private fun retryCounterText(retries: Int): String {
    // retries=1 -> attempt 2/3, retries=2 -> attempt 3/3
    val attempt = (retries + 1).coerceIn(1, 3)
    val sup = arrayOf("", "¹", "²", "³")
    return "${sup[attempt]}⁄₃"   // e.g. ¹⁄₃ ²⁄₃ ³⁄₃
}

private fun tickFor(s: MessageState): String = when (s) {
    MessageState.SENDING     -> "clock"
    MessageState.SENT        -> "sent"
    MessageState.ACKED       -> "delivered"
    MessageState.FAILED      -> "failed"
    MessageState.SERVER_SENT -> "↗"
}

private fun tickColor(s: MessageState): Color = when (s) {
    MessageState.ACKED       -> Color(0xFF86EFAC)
    MessageState.FAILED      -> Err
    MessageState.SERVER_SENT -> Color(0xFFF59E0B)
    else                     -> TextDim
}
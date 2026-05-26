package uk.aprsnet.client.ui.contacts

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.aprsnet.client.AprsViewModel
import uk.aprsnet.client.ui.theme.Accent
import uk.aprsnet.client.ui.theme.TextDim

/** Saved contacts - tap to message, with add/delete. */
@Composable
fun ContactsScreen(
    vm: AprsViewModel,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val contacts by vm.contacts.collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (contacts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No contacts yet", color = TextDim)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(contacts) { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                c.callsign,
                                color = Accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (c.alias.isNotEmpty()) {
                                Text(c.alias, color = TextDim, fontSize = 12.sp)
                            }
                        }
                        IconButton(onClick = { onMessage(c.callsign) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Message,
                                contentDescription = "Message",
                                tint = Accent
                            )
                        }
                        IconButton(onClick = { vm.deleteContact(c) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = TextDim
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add contact")
        }
    }

    if (showAdd) {
        var call by remember { mutableStateOf("") }
        var alias by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add contact") },
            text = {
                Column {
                    OutlinedTextField(
                        value = call,
                        onValueChange = { call = it },
                        label = { Text("Callsign") },
                        singleLine = true
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = alias,
                        onValueChange = { alias = it },
                        label = { Text("Alias (optional)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (call.isNotBlank()) vm.addContact(call, alias)
                    showAdd = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel") }
            }
        )
    }
}
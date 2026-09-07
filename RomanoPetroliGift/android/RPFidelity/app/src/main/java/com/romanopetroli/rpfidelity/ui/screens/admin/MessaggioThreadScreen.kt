package com.romanopetroli.rpfidelity.ui.screens.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.ui.components.ChatBubble
import com.romanopetroli.rpfidelity.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessaggioThreadScreen(adminViewModel: AdminViewModel, onBack: () -> Unit) {
    var messaggio by remember { mutableStateOf("") }

    val cliente by adminViewModel.threadCliente.collectAsState()
    val messaggi by adminViewModel.messaggiThread.collectAsState()
    val loading by adminViewModel.loading.collectAsState()
    val error by adminViewModel.error.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messaggi.size) {
        if (messaggi.isNotEmpty()) listState.animateScrollToItem(messaggi.size - 1)
    }

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = cliente?.let { "${it.nome} ${it.cognome}" } ?: "Chat",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(messaggi) { m ->
                    ChatBubble(
                        autore = if (m.daAdmin) "Tu" else (cliente?.nome ?: "Cliente"),
                        testo = m.messaggio,
                        daDestra = m.daAdmin
                    )
                }
            }

            if (!error.isNullOrBlank()) {
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messaggio,
                    onValueChange = { messaggio = it },
                    label = { Text("Rispondi") },
                    modifier = Modifier.weight(1f)
                )
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
                } else {
                    IconButton(
                        enabled = messaggio.isNotBlank() && cliente != null,
                        onClick = {
                            cliente?.let { adminViewModel.rispondiMessaggio(it.id, messaggio) }
                            messaggio = ""
                        }
                    ) { Icon(Icons.Filled.Send, contentDescription = "Invia") }
                }
            }
        }
    }
}

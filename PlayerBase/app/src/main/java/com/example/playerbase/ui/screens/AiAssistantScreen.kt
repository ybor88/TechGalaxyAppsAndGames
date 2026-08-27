package com.example.playerbase.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.playerbase.data.PlayerAssistant
import com.example.playerbase.ui.theme.BrandColors
import com.example.playerbase.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

private data class ChatMessage(val text: String, val fromUser: Boolean)

/**
 * Assistente locale (nessuna connessione esterna, nessuna chiave API): risponde
 * a domande sui giocatori usando solo i dati già presenti in anagrafica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val players by viewModel.players.collectAsState()
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                "Ciao! Chiedimi il nome di un giocatore, una squadra, un ruolo, oppure \"quanti giocatori\" o \"età media\".",
                fromUser = false
            )
        )
    }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun send() {
        val text = input.trim()
        if (text.isBlank()) return
        messages.add(ChatMessage(text, fromUser = true))
        messages.add(ChatMessage(PlayerAssistant.answer(text, players), fromUser = false))
        input = ""
        scope.launch { listState.animateScrollToItem(messages.size - 1) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assistente IA") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.navy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                itemsIndexed(messages) { _, message ->
                    ChatBubble(message)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Chiedi info su un giocatore...") },
                    singleLine = true,
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { send() })
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { send() }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Invia")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (message.fromUser) BrandColors.navy else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                message.text,
                color = if (message.fromUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (message.fromUser) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

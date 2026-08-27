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
import com.example.playerbase.data.WebSearchService
import com.example.playerbase.ui.theme.BrandColors
import com.example.playerbase.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

private data class ChatMessage(val text: String, val fromUser: Boolean, val searching: Boolean = false)

/**
 * Assistente IA: risponde a domande sui giocatori usando i dati già presenti
 * in anagrafica; se non trova nulla, cerca automaticamente la risposta sul
 * web tramite [WebSearchService] (gratuito, nessuna chiave API richiesta).
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
                "Ciao! Chiedimi il nome di un giocatore, una squadra, un ruolo, oppure \"quanti giocatori\" o \"età media\". Se non trovo nulla in anagrafica, provo a cercare la risposta su internet.",
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
        input = ""

        val localAnswer = PlayerAssistant.answer(text, players)
        if (localAnswer.startsWith(PlayerAssistant.NOT_FOUND_PREFIX)) {
            messages.add(ChatMessage("Cerco su internet...", fromUser = false, searching = true))
            val searchingIndex = messages.size - 1
            scope.launch {
                listState.animateScrollToItem(messages.size - 1)
                val webAnswer = WebSearchService.search(text)
                messages[searchingIndex] = ChatMessage(
                    webAnswer ?: "$localAnswer\nNon ho trovato nulla neanche cercando su internet.",
                    fromUser = false
                )
            }
        } else {
            messages.add(ChatMessage(localAnswer, fromUser = false))
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
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
        Row(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (message.fromUser) BrandColors.navy else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (message.searching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                message.text,
                color = if (message.fromUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (message.fromUser) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

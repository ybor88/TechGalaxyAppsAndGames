package com.matchmind.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matchmind.app.dati.SchedinaCompleta
import com.matchmind.app.dati.SelezioneEntity
import com.matchmind.app.dominio.Scommessa
import com.matchmind.app.dominio.EsitoSelezione
import com.matchmind.app.dominio.Leghe
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoSchedina
import com.matchmind.app.dominio.Strategia

// Elenco delle schedine, dettaglio e validazione sui risultati reali. © Roberto Di Flumeri

private fun SchedinaCompleta.stato() = StatoSchedina.valueOf(schedina.stato)
private fun SchedinaCompleta.sport() = Sport.valueOf(schedina.sport)

@Composable
fun SchermataSchedine(vm: MatchMindViewModel, apri: (Long) -> Unit) {
    val schedine by vm.schedine.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var filtro by rememberSaveable { mutableStateOf<String?>(null) }
    val sportScelto = listOf(null, Sport.CALCIO, Sport.BASKET)[tab]
    val elenco = schedine
        .filter { sportScelto == null || it.sport() == sportScelto }
        .filter { filtro == null || it.schedina.stato == filtro }

    Column {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Schedine", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Button(onClick = { vm.verifica() }, shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Colori.Verde, contentColor = Colori.Sfondo)) {
                Icon(Icons.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Verifica")
            }
        }
        TabRow(
            selectedTabIndex = tab, containerColor = Colori.Sfondo, contentColor = Colori.Verde,
            indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = if (tab == 2) Colori.Arancio else Colori.Verde) },
        ) {
            listOf("Tutte", "Calcio", "Basket").forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) },
                    selectedContentColor = if (i == 2) Colori.Arancio else Colori.Verde, unselectedContentColor = Colori.Tenue)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to "Tutte", StatoSchedina.IN_CORSO.name to "In attesa", StatoSchedina.VINTA.name to "Vinte", StatoSchedina.PERSA.name to "Perse")
                .forEach { (valore, etichetta) ->
                    FilterChip(selected = filtro == valore, onClick = { filtro = valore }, label = { Text(etichetta) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Colori.Verde.copy(alpha = 0.2f), selectedLabelColor = Colori.Verde))
                }
        }
        if (elenco.isEmpty()) {
            Text(if (schedine.isEmpty()) "Nessuna schedina ancora: generane una dalla Home." else "Nessuna schedina con questo filtro.",
                color = Colori.Tenue, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(elenco, key = { it.schedina.id }) { CartaSchedina(it) { apri(it.schedina.id) } }
        }
    }
}

@Composable
fun CartaSchedina(s: SchedinaCompleta, onClick: () -> Unit) {
    val stato = s.stato()
    val colore = coloreStato(stato)
    val sport = s.sport()
    val indovinate = s.selezioni.count { it.esito == EsitoSelezione.VINTA.name }
    Scheda(Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick), bordo = colore.copy(alpha = if (stato == StatoSchedina.IN_CORSO) 0.4f else 0.8f)) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(colore))
            Spacer(Modifier.width(12.dp))
            Icon(if (sport == Sport.CALCIO) Icons.Filled.SportsSoccer else Icons.Filled.SportsBasketball, contentDescription = sport.etichetta,
                tint = if (sport == Sport.CALCIO) Colori.Verde else Colori.Arancio, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${sport.etichetta} · ${s.selezioni.size} partite", style = MaterialTheme.typography.titleMedium)
                Text("${s.schedina.creataIl.dataOra()} · quota ${s.schedina.quotaTotale.quota()}", style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                Text("Indovinate $indovinate su ${s.selezioni.size}", style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
            }
            Column(horizontalAlignment = Alignment.End) {
                Pillola(stato.etichetta, colore)
                Spacer(Modifier.height(6.dp))
                val importo = when (stato) {
                    StatoSchedina.VINTA -> "+${s.schedina.vincita.euro()}"
                    StatoSchedina.PERSA -> "-${s.schedina.puntata.euro()}"
                    StatoSchedina.RIMBORSATA -> s.schedina.vincita.euro()
                    StatoSchedina.IN_CORSO -> (s.schedina.puntata * s.schedina.quotaTotale).euro()
                }
                Text(importo, fontWeight = FontWeight.Bold, color = if (stato == StatoSchedina.IN_CORSO) Colori.Testo else colore)
                if (stato == StatoSchedina.IN_CORSO) Text("potenziale", style = MaterialTheme.typography.labelSmall, color = Colori.Tenue)
            }
        }
    }
}

@Composable
fun DettaglioSchedina(vm: MatchMindViewModel, id: Long, chiudi: () -> Unit) {
    val schedine by vm.schedine.collectAsState()
    val s = schedine.firstOrNull { it.schedina.id == id }
    if (s == null) {
        Text("Schedina non trovata", modifier = Modifier.padding(16.dp))
        return
    }
    val stato = s.stato()
    val colore = coloreStato(stato)
    val sport = s.sport()
    val strategia = runCatching { Strategia.valueOf(s.schedina.strategia) }.getOrNull()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = chiudi) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
                Text("Schedina ${sport.etichetta}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Pillola(stato.etichetta, colore)
            }
        }
        item {
            Scheda(bordo = colore) {
                Text(when (stato) {
                    StatoSchedina.VINTA -> "Indovinata! Tutti i pronostici sono corretti."
                    StatoSchedina.PERSA -> s.selezioni.count { it.esito == EsitoSelezione.PERSA.name }.let { n -> if (n == 1) "Persa: 1 pronostico sbagliato." else "Persa: $n pronostici sbagliati." }
                    StatoSchedina.RIMBORSATA -> "Rimborsata: tutte le partite sono state annullate."
                    StatoSchedina.IN_CORSO -> "In attesa dei risultati: premi Verifica dopo le partite."
                }, style = MaterialTheme.typography.titleMedium, color = colore)
                RigaValori(
                    Triple("Puntata", s.schedina.puntata.euro(), Colori.Testo),
                    Triple("Quota", s.schedina.quotaTotale.quota(), Colori.Testo),
                    Triple(if (stato == StatoSchedina.IN_CORSO) "Vincita potenziale" else "Incassato",
                        (if (stato == StatoSchedina.IN_CORSO) s.schedina.puntata * s.schedina.quotaTotale else s.schedina.vincita).euro(),
                        if (stato == StatoSchedina.PERSA) Colori.Rosso else Colori.Verde),
                )
                Text("Giocata ${s.schedina.creataIl.dataOra()} · strategia ${strategia?.etichetta ?: "-"} · probabilità stimata di vincerla ${s.schedina.probabilita.percentualeFine()}",
                    style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                s.schedina.verificataIl?.let { Text("Ultima verifica: ${it.dataOra()}", style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue) }
                if (stato == StatoSchedina.IN_CORSO) {
                    Button(onClick = { vm.verifica() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Colori.Verde, contentColor = Colori.Sfondo)) {
                        Text("Verifica risultati ora")
                    }
                }
            }
        }
        item { Text("Pronostici", style = MaterialTheme.typography.titleMedium) }
        items(s.selezioni.sortedBy { it.inizio }, key = { it.id }) { RigaSelezione(it) }
    }
}

fun descriviScelta(sel: SelezioneEntity): String =
    Scommessa.di(sel.scelta)?.descrivi(sel.casa, sel.ospite) ?: sel.scelta

@Composable
fun RigaSelezione(sel: SelezioneEntity) {
    val esito = EsitoSelezione.valueOf(sel.esito)
    val colore = coloreEsito(esito)
    Scheda(bordo = colore.copy(alpha = if (esito == EsitoSelezione.IN_ATTESA) 0.3f else 0.7f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Etichetta("${Leghe.nome(sel.lega)} · ${sel.inizio.dataOra()}")
                Text("${sel.casa} – ${sel.ospite}", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(sel.risultato ?: "–", style = MaterialTheme.typography.titleLarge, color = if (sel.risultato == null) Colori.Grigio else Colori.Testo)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(descriviScelta(sel), color = Colori.Verde, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Pillola(when (esito) {
                EsitoSelezione.VINTA -> "Indovinata"
                EsitoSelezione.PERSA -> "Sbagliata"
                EsitoSelezione.NULLA -> "Annullata"
                EsitoSelezione.IN_ATTESA -> "In attesa"
            }, colore)
        }
        Text(buildString {
            append("Quota ${sel.quota.quota()}")
            if (sel.quotaStimata) append(" (stimata)")
            append(" · probabilità ${sel.probabilita.percentuale()}")
            sel.pMercato?.let { append(" · mercato ${it.percentuale()}") }
            sel.pModello?.let { append(" · statistica ${it.percentuale()}") }
        }, style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
    }
}

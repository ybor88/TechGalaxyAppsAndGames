package com.matchmind.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.matchmind.app.R
import com.matchmind.app.dominio.Contabilita
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoSchedina

// Struttura dell'app: navigazione in basso e cruscotto. © Roberto Di Flumeri

enum class Sezione(val titolo: String, val icona: ImageVector) {
    CRUSCOTTO("Home", Icons.Filled.Home),
    SCHEDINE("Schedine", Icons.Filled.ReceiptLong),
    STATISTICHE("Statistiche", Icons.Filled.Insights),
    IMPOSTAZIONI("Impostazioni", Icons.Filled.Settings),
}

@Composable
fun AppMatchMind(vm: MatchMindViewModel) {
    var sezione by rememberSaveable { mutableStateOf(Sezione.CRUSCOTTO) }
    var dettaglio by rememberSaveable { mutableLongStateOf(-1L) }
    val preferenze by vm.preferenze.collectAsState()
    val attivita by vm.attivita.collectAsState()
    val messaggio by vm.messaggio.collectAsState()
    val proposta by vm.proposta.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(messaggio) {
        messaggio?.let {
            snackbar.showSnackbar(it)
            vm.messaggioLetto()
        }
    }
    BackHandler(enabled = dettaglio >= 0 || sezione != Sezione.CRUSCOTTO) {
        if (dettaglio >= 0) dettaglio = -1 else sezione = Sezione.CRUSCOTTO
    }

    Scaffold(
        containerColor = Colori.Sfondo,
        snackbarHost = {
            SnackbarHost(snackbar) { dati ->
                Snackbar(dati, containerColor = Colori.SuperficieAlta, contentColor = Colori.Testo, actionColor = Colori.Verde,
                    shape = RoundedCornerShape(16.dp))
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Colori.Superficie) {
                Sezione.entries.forEach { s ->
                    NavigationBarItem(
                        selected = sezione == s && dettaglio < 0,
                        onClick = { sezione = s; dettaglio = -1 },
                        icon = { Icon(s.icona, contentDescription = null) },
                        label = { Text(s.titolo) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Colori.Sfondo, indicatorColor = Colori.Verde,
                            selectedTextColor = Colori.Verde, unselectedIconColor = Colori.Tenue, unselectedTextColor = Colori.Tenue,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                dettaglio >= 0 -> DettaglioSchedina(vm, dettaglio) { dettaglio = -1 }
                sezione == Sezione.CRUSCOTTO -> Cruscotto(vm, apri = { dettaglio = it }, vaiASchedine = { sezione = Sezione.SCHEDINE })
                sezione == Sezione.SCHEDINE -> SchermataSchedine(vm, apri = { dettaglio = it })
                sezione == Sezione.STATISTICHE -> SchermataStatistiche(vm)
                sezione == Sezione.IMPOSTAZIONI -> SchermataImpostazioni(vm)
            }
        }
    }

    if (preferenze?.configurato == false) PrimoAvvio(vm)
    proposta?.let { DialogoProposta(vm, it) }
    attivita?.let { Attesa(it) }
}

@Composable
fun Attesa(testo: String) {
    Dialog(onDismissRequest = {}) {
        Scheda {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = Colori.Verde, modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(16.dp))
                Text(testo, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun Intestazione(sottotitolo: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Image(painterResource(R.drawable.app_logo), contentDescription = "Logo MatchMind",
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(12.dp))
        Column {
            Row {
                Text("Match", style = MaterialTheme.typography.headlineSmall, color = Colori.Testo)
                Text("Mind", style = MaterialTheme.typography.headlineSmall, color = Colori.Verde)
            }
            Text(sottotitolo, style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
        }
    }
}

@Composable
fun Cruscotto(vm: MatchMindViewModel, apri: (Long) -> Unit, vaiASchedine: () -> Unit) {
    val movimenti by vm.movimenti.collectAsState()
    val schedine by vm.schedine.collectAsState()
    val bilancio = Contabilita.bilancio(movimenti)
    val inAttesa = schedine.count { it.schedina.stato == StatoSchedina.IN_CORSO.name }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Intestazione("Pronostici sulle partite reali") }
        item {
            Scheda(bordo = if (bilancio.saldo >= 0) Colori.Verde.copy(alpha = 0.5f) else Colori.Rosso.copy(alpha = 0.6f)) {
                Etichetta("Saldo virtuale")
                Text(bilancio.saldo.euro(), style = MaterialTheme.typography.headlineLarge,
                    color = if (bilancio.saldo >= 0) Colori.Testo else Colori.Rosso)
                val utile = bilancio.utileMetodo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pillola(if (utile >= 0) "Credito ${utile.euroConSegno()}" else "Debito ${utile.euroConSegno()}",
                        if (utile >= 0) Colori.Verde else Colori.Rosso)
                    Spacer(Modifier.width(8.dp))
                    Text("generato dal metodo", style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                }
                RigaValori(
                    Triple("Ricaricato", bilancio.ricaricato.euro(), Colori.Testo),
                    Triple("Puntato", bilancio.puntato.euro(), Colori.Testo),
                    Triple("Incassato", bilancio.incassato.euro(), Colori.Verde),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PulsanteGenera("Schedina\nCalcio", Icons.Filled.SportsSoccer, Colori.Verde, Modifier.weight(1f)) { vm.genera(Sport.CALCIO) }
                PulsanteGenera("Schedina\nBasket", Icons.Filled.SportsBasketball, Colori.Arancio, Modifier.weight(1f)) { vm.genera(Sport.BASKET) }
            }
        }
        item {
            OutlinedButton(onClick = { vm.verifica() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.FactCheck, contentDescription = null, tint = Colori.Verde)
                Spacer(Modifier.width(8.dp))
                Text(if (inAttesa > 0) "Verifica risultati ($inAttesa in attesa)" else "Verifica risultati", color = Colori.Testo)
            }
        }
        if (schedine.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ultime schedine", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("Tutte →", color = Colori.Verde, style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = vaiASchedine).padding(6.dp))
                }
            }
            items(schedine.take(3).size) { i -> CartaSchedina(schedine[i]) { apri(schedine[i].schedina.id) } }
        } else {
            item {
                Scheda {
                    Text("Come funziona", style = MaterialTheme.typography.titleMedium)
                    Text("1. Genera una schedina di calcio o basket: MatchMind legge partite e quote reali e sceglie solo i pronostici sicuri (vincente, pareggio, doppia chance, Goal/NoGoal, Under/Over).\n" +
                        "2. Gioca con il credito virtuale.\n3. Dopo le partite premi Verifica: la schedina diventa verde se le hai indovinate tutte, rossa altrimenti.\n" +
                        "4. Ogni risultato insegna qualcosa al modello: le schedine successive migliorano con l'esperienza.",
                        style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                }
            }
        }
        item {
            Text("© Roberto Di Flumeri · solo credito virtuale, nessuna scommessa reale",
                style = MaterialTheme.typography.labelSmall, color = Colori.Grigio, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
fun PulsanteGenera(testo: String, icona: ImageVector, colore: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colore.copy(alpha = 0.14f), contentColor = colore),
        contentPadding = PaddingValues(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icona, contentDescription = null, modifier = Modifier.size(34.dp))
            Text(testo, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Colori.Testo)
        }
    }
}

package com.matchmind.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.matchmind.app.dominio.Contabilita
import com.matchmind.app.dominio.Esperienza
import com.matchmind.app.dominio.Leghe
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.StatoSchedina

// Statistiche: andamento mensile, saldo, rendimento del metodo e apprendimento. © Roberto Di Flumeri

@Composable
fun SchermataStatistiche(vm: MatchMindViewModel) {
    val movimenti by vm.movimenti.collectAsState()
    val schedine by vm.schedine.collectAsState()
    val esperienze by vm.esperienza.collectAsState()
    val inAttesa by vm.osservazioniInAttesa.collectAsState()
    var filtro by rememberSaveable { mutableStateOf<Sport?>(null) }

    val sport = filtro
    val chiuse = vm.schedineChiuse(sport)
    val mesi = Contabilita.riepilogoMensile(chiuse)
    val giocate = schedine.count { sport == null || it.schedina.sport == sport.name }
    val vinte = chiuse.count { it.stato == StatoSchedina.VINTA }
    val perse = chiuse.count { it.stato == StatoSchedina.PERSA }
    val puntato = chiuse.filter { it.stato != StatoSchedina.RIMBORSATA }.sumOf { it.puntata }
    val incassato = chiuse.filter { it.stato == StatoSchedina.VINTA }.sumOf { it.vincita }
    val utile = incassato - puntato

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Statistiche", style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null to "Tutto", Sport.CALCIO to "Calcio", Sport.BASKET to "Basket").forEach { (s, t) ->
                    FilterChip(selected = filtro == s, onClick = { filtro = s }, label = { Text(t) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = (if (s == Sport.BASKET) Colori.Arancio else Colori.Verde).copy(alpha = 0.2f),
                            selectedLabelColor = if (s == Sport.BASKET) Colori.Arancio else Colori.Verde))
                }
            }
        }
        item {
            Scheda(bordo = if (utile >= 0) Colori.Verde.copy(alpha = 0.6f) else Colori.Rosso.copy(alpha = 0.6f)) {
                Etichetta("Il metodo crea credito o debito?")
                Text(if (chiuse.isEmpty()) "Ancora nessuna schedina chiusa" else if (utile >= 0) "Credito ${utile.euroConSegno()}" else "Debito ${utile.euroConSegno()}",
                    style = MaterialTheme.typography.headlineSmall, color = if (chiuse.isEmpty()) Colori.Tenue else if (utile >= 0) Colori.Verde else Colori.Rosso)
                RigaValori(
                    Triple("Giocate", "$giocate", Colori.Testo),
                    Triple("Vinte", "$vinte", Colori.Verde),
                    Triple("Perse", "$perse", Colori.Rosso),
                )
                RigaValori(
                    Triple("Puntato", puntato.euro(), Colori.Testo),
                    Triple("Incassato", incassato.euro(), Colori.Verde),
                    Triple("Rendimento", if (puntato > 0) (utile / puntato).percentuale() else "–", if (utile >= 0) Colori.Verde else Colori.Rosso),
                )
            }
        }
        item {
            Scheda {
                Text("Andamento mensile", style = MaterialTheme.typography.titleMedium)
                if (mesi.isEmpty()) Text("Il grafico compare quando la prima schedina viene verificata.", color = Colori.Tenue)
                else {
                    GraficoMensile(mesi)
                    HorizontalDivider(color = Colori.Bordo)
                    mesi.takeLast(6).reversed().forEach { m ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(m.mese.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ITALY)).replaceFirstChar { it.uppercase() },
                                modifier = Modifier.weight(1.3f))
                            Text("+${m.incassato.euro()}", color = Colori.Verde, modifier = Modifier.weight(1f))
                            Text("-${m.perso.euro()}", color = Colori.Rosso, modifier = Modifier.weight(1f))
                            Text(m.netto.euroConSegno(), fontWeight = FontWeight.Bold, color = if (m.netto >= 0) Colori.Verde else Colori.Rosso, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (filtro == null) {
            item {
                Scheda {
                    Text("Saldo nel tempo", style = MaterialTheme.typography.titleMedium)
                    val punti = Contabilita.andamento(movimenti)
                    if (punti.size < 2) Text("Servono almeno due movimenti.", color = Colori.Tenue) else GraficoSaldo(punti)
                    Text("Comprende ricariche e azzeramenti del credito virtuale.", style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                }
            }
        }
        (if (sport == null) Sport.entries else listOf(sport)).forEach { s ->
            item { CartaEsperienza(esperienze[s] ?: Esperienza.vuota(s), inAttesa[s] ?: 0) }
        }
        item {
            Scheda {
                Text("Come ragiona MatchMind", style = MaterialTheme.typography.titleMedium)
                Text("• Mercato: le quote reali del bookmaker (1X2 e Under/Over) diventano probabilità togliendo il margine.\n" +
                    "• Statistica: calcio con modello di Poisson sui gol fatti e subiti, basket con la differenza punti; vantaggio del campo di casa.\n" +
                    "• Gol: dai gol attesi coerenti con le quote si ricavano pareggio, doppia chance, Goal/NoGoal e Under/Over.\n" +
                    "• Esperienza: ogni partita analizzata viene confrontata con il risultato reale. Il modello impara quanto pesare mercato e statistica, corregge le probabilità di ogni tipo di pronostico e premia le leghe più prevedibili.\n" +
                    "• Schedina: niente numero fisso di partite; si aggiungono pronostici sicuri finché la probabilità di vincerla resta sopra la soglia scelta.\n" +
                    "• Una schedina multipla vince solo se tutti i pronostici sono giusti, e il margine del bookmaker si moltiplica su ogni partita. Il grafico mostra onestamente se nel tempo il metodo crea credito o debito.",
                    style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
            }
        }
    }
}

@Composable
fun CartaEsperienza(e: Esperienza, inAttesa: Int) {
    val colore = if (e.sport == Sport.CALCIO) Colori.Verde else Colori.Arancio
    Scheda {
        Text("Esperienza ${e.sport.etichetta.lowercase()}", style = MaterialTheme.typography.titleMedium, color = colore)
        if (e.osservazioni == 0) {
            Text("Nessuna partita conclusa da cui imparare. Ogni schedina generata registra tutte le partite analizzate: dopo la verifica il modello inizia a imparare.",
                style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
            if (inAttesa > 0) Text("Partite in attesa di risultato: $inAttesa", style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
            return@Scheda
        }
        RigaValori(
            Triple("Partite studiate", "${e.osservazioni}", Colori.Testo),
            Triple("Favorite vincenti", e.precisione.percentuale(), colore),
            Triple("Peso quote", e.pesoMercato.percentuale(), Colori.Testo),
        )
        if (e.osservazioni < Esperienza.MINIMO_PER_IMPARARE) {
            Text("Ancora poche partite: le correzioni restano prudenti fino a ${Esperienza.MINIMO_PER_IMPARARE}.", style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
        }
        e.brierCombinato?.let { b ->
            Text("Errore medio (Brier): ${String.format(java.util.Locale.ITALY, "%.3f", b)}" +
                (e.brierMercato?.let { " · solo quote ${String.format(java.util.Locale.ITALY, "%.3f", it)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
        }
        if (e.riepilogo.isNotEmpty()) {
            HorizontalDivider(color = Colori.Bordo)
            Etichetta("Per tipo di pronostico: previsto vs avvenuto")
            e.riepilogo.forEach { r ->
                Row(Modifier.fillMaxWidth()) {
                    Text(r.mercato.etichetta, modifier = Modifier.weight(1.4f))
                    Text("${r.n}", color = Colori.Tenue, modifier = Modifier.weight(0.5f))
                    Text(r.prevista.percentuale(), modifier = Modifier.weight(0.6f))
                    Text(r.reale.percentuale(), fontWeight = FontWeight.Bold,
                        color = if (r.reale >= r.prevista - 0.03) Colori.Verde else Colori.Rosso, modifier = Modifier.weight(0.6f))
                }
            }
        }
        val fasce = e.fasce[com.matchmind.app.dominio.Mercato.VINCENTE].orEmpty().filter { it.n > 0 }
        if (fasce.isNotEmpty()) {
            HorizontalDivider(color = Colori.Bordo)
            Etichetta("Calibrazione del vincente: prevista vs reale")
            fasce.forEach { f ->
                Row(Modifier.fillMaxWidth()) {
                    Text(f.etichetta, modifier = Modifier.weight(1f))
                    Text("${f.n} previsioni", color = Colori.Tenue, modifier = Modifier.weight(1f))
                    Text(f.prevista.percentuale(), modifier = Modifier.weight(0.7f))
                    Text(f.reale.percentuale(), fontWeight = FontWeight.Bold,
                        color = if (f.reale >= f.prevista - 0.03) Colori.Verde else Colori.Rosso, modifier = Modifier.weight(0.7f))
                }
            }
        }
        val leghe = e.leghe.values.filter { it.n >= 5 }.sortedByDescending { it.fattore }
        if (leghe.isNotEmpty()) {
            HorizontalDivider(color = Colori.Bordo)
            Etichetta("Leghe più prevedibili")
            leghe.take(6).forEach { l ->
                Row(Modifier.fillMaxWidth()) {
                    Text(Leghe.nome(l.lega), modifier = Modifier.weight(1.4f))
                    Text("${l.vinte}/${l.n}", color = Colori.Tenue, modifier = Modifier.weight(0.7f))
                    Text("×${String.format(java.util.Locale.ITALY, "%.2f", l.fattore)}",
                        color = if (l.fattore >= 1) Colori.Verde else Colori.Rosso, modifier = Modifier.weight(0.6f))
                }
            }
        }
    }
}

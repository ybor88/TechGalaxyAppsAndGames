package com.matchmind.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.matchmind.app.R
import com.matchmind.app.dati.Preferenze
import com.matchmind.app.dati.Repository
import com.matchmind.app.dominio.Contabilita
import com.matchmind.app.dominio.Leghe
import com.matchmind.app.dominio.Mercato
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.matchmind.app.dominio.Sport
import com.matchmind.app.dominio.Strategia
import kotlin.math.roundToInt

// Impostazioni, gestione del credito virtuale e dialoghi. © Roberto Di Flumeri

/** Accetta "1.000,50", "1000.5", "€ 20". */
fun leggiImporto(testo: String): Double? {
    var t = testo.replace("€", "").replace(" ", "").trim()
    if (t.contains(',')) t = t.replace(".", "").replace(',', '.')
    return t.toDoubleOrNull()?.takeIf { it > 0 && it < 100_000_000 }
}

@Composable
private fun CampoImporto(valore: String, onCambia: (String) -> Unit, etichetta: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = valore, onValueChange = onCambia, label = { Text(etichetta) }, singleLine = true, prefix = { Text("€ ") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = valore.isNotBlank() && leggiImporto(valore) == null,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Colori.Verde, focusedLabelColor = Colori.Verde, cursorColor = Colori.Verde),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchermataImpostazioni(vm: MatchMindViewModel) {
    val pref by vm.preferenze.collectAsState()
    val movimenti by vm.movimenti.collectAsState()
    val p = pref ?: Preferenze()
    val saldo = Contabilita.saldo(movimenti)
    var ricarica by remember { mutableStateOf(false) }
    var azzera by remember { mutableStateOf(false) }
    var cancella by remember { mutableStateOf(false) }
    var puntata by remember(p.puntata) { mutableStateOf(p.puntata.toString().removeSuffix(".0").replace('.', ',')) }
    var giorni by remember(p.giorni) { mutableFloatStateOf(p.giorni.toFloat()) }
    var sicurezza by remember(p.sicurezza) { mutableFloatStateOf(p.sicurezza.toFloat()) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Impostazioni", style = MaterialTheme.typography.headlineSmall) }
        item {
            Scheda {
                Etichetta("Credito virtuale")
                Text(saldo.euro(), style = MaterialTheme.typography.headlineSmall, color = if (saldo >= 0) Colori.Testo else Colori.Rosso)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { ricarica = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Colori.Verde, contentColor = Colori.Sfondo)) { Text("Ricarica") }
                    OutlinedButton(onClick = { azzera = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Text("Azzera credito", color = Colori.Arancio)
                    }
                }
                Text("Azzera porta il saldo a zero (anche se in debito); poi puoi ricaricare un nuovo credito. Lo storico delle schedine e il risultato del metodo restano.",
                    style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
            }
        }
        item {
            Scheda {
                Etichetta("Schedina")
                CampoImporto(puntata, { puntata = it }, "Puntata predefinita")
                Text("Sicurezza della schedina: almeno ${(sicurezza * 100).roundToInt()}%", style = MaterialTheme.typography.titleMedium)
                Slider(value = sicurezza, onValueChange = { sicurezza = (it * 20).roundToInt() / 20f }, valueRange = 0.1f..0.6f, steps = 9,
                    colors = SliderDefaults.colors(thumbColor = Colori.Verde, activeTrackColor = Colori.Verde))
                Text("Il numero di partite lo sceglie MatchMind: aggiunge solo pronostici sicuri finché la probabilità di vincere " +
                    "l'intera schedina resta sopra questa soglia (al massimo ${com.matchmind.app.dominio.Generatore.MAX_PARTITE}). " +
                    "Più alta la soglia, meno partite e vincite più basse.",
                    style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                Text("Partite dei prossimi ${giorni.roundToInt()} giorni", style = MaterialTheme.typography.titleMedium)
                Slider(value = giorni, onValueChange = { giorni = it }, valueRange = 1f..7f, steps = 5,
                    colors = SliderDefaults.colors(thumbColor = Colori.Arancio, activeTrackColor = Colori.Arancio))
                Text("Tipi di pronostico (calcio)", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mercato.entries.forEach { m ->
                        val attivo = m == Mercato.VINCENTE || m in p.mercati
                        FilterChip(
                            selected = attivo,
                            enabled = m != Mercato.VINCENTE,
                            onClick = { vm.salva(p.copy(mercati = if (m in p.mercati) p.mercati - m else p.mercati + m)) },
                            label = { Text(m.etichetta) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Colori.Verde.copy(alpha = 0.2f), selectedLabelColor = Colori.Verde,
                                disabledSelectedContainerColor = Colori.Verde.copy(alpha = 0.12f), disabledLabelColor = Colori.Verde.copy(alpha = 0.7f)),
                        )
                    }
                }
                Text("Vincente sempre attivo. Per ogni partita MatchMind sceglie la giocata più sicura tra i tipi attivi. Nel basket solo vincente.",
                    style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                Text("Strategia", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Strategia.entries.forEachIndexed { i, s ->
                        SegmentedButton(selected = p.strategia == s, onClick = { vm.salva(p.copy(strategia = s)) },
                            shape = SegmentedButtonDefaults.itemShape(i, Strategia.entries.size),
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = Colori.Verde.copy(alpha = 0.2f), activeContentColor = Colori.Verde)) {
                            Text(s.etichetta)
                        }
                    }
                }
                Text(p.strategia.descrizione, style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                Button(
                    onClick = {
                        val importo = leggiImporto(puntata)
                        if (importo == null) vm.mostra("Puntata non valida")
                        else { vm.salva(p.copy(puntata = importo, giorni = giorni.roundToInt(), sicurezza = sicurezza.toDouble())); vm.mostra("Impostazioni salvate") }
                    },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Colori.Verde, contentColor = Colori.Sfondo),
                ) { Text("Salva") }
            }
        }
        item {
            Scheda {
                Etichetta("Fonti dei dati")
                Text("Partite, risultati reali e classifiche: feed pubblico ESPN. Quote: DraftKings (riportate da ESPN). Se una partita non è quotata la quota viene stimata dalla probabilità e indicata come \"stimata\".",
                    style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                Text("Calcio: ${Leghe.CALCIO.joinToString { it.nome }}", style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                Text("Basket: ${Leghe.BASKET.joinToString { it.nome }}", style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
            }
        }
        item {
            Scheda(bordo = Colori.Arancio.copy(alpha = 0.5f)) {
                Etichetta("Gioco responsabile", Colori.Arancio)
                Text("MatchMind è una simulazione con credito virtuale: nessuna scommessa reale, nessun pagamento. Nessun metodo può garantire vincite. " +
                    "Il gioco d'azzardo può causare dipendenza: se il gioco ti crea problemi chiama il Telefono Verde Nazionale 800 558822 (gratuito e anonimo). Vietato ai minori di 18 anni.",
                    style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
            }
        }
        item {
            OutlinedButton(onClick = { cancella = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Cancella tutti i dati", color = Colori.Rosso)
            }
        }
        item {
            Text("MatchMind 1.1 · © Roberto Di Flumeri", style = MaterialTheme.typography.labelSmall, color = Colori.Grigio,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }

    if (ricarica) DialogoRicarica(onConferma = { vm.ricarica(it); ricarica = false }, onAnnulla = { ricarica = false })
    if (azzera) AlertDialog(
        onDismissRequest = { azzera = false },
        title = { Text("Azzerare il credito?") },
        text = { Text("Il saldo di ${saldo.euro()} verrà portato a zero. Potrai poi ricaricare un nuovo credito virtuale. Schedine e statistiche restano.") },
        confirmButton = { TextButton(onClick = { vm.azzera(); azzera = false }) { Text("Azzera", color = Colori.Arancio) } },
        dismissButton = { TextButton(onClick = { azzera = false }) { Text("Annulla") } },
        containerColor = Colori.Superficie,
        tonalElevation = 0.dp,
    )
    if (cancella) AlertDialog(
        onDismissRequest = { cancella = false },
        title = { Text("Cancellare tutto?") },
        text = { Text("Schedine, movimenti ed esperienza accumulata verranno eliminati. L'operazione non si può annullare.") },
        confirmButton = { TextButton(onClick = { vm.cancellaTutto(); cancella = false }) { Text("Cancella", color = Colori.Rosso) } },
        dismissButton = { TextButton(onClick = { cancella = false }) { Text("Annulla") } },
        containerColor = Colori.Superficie,
        tonalElevation = 0.dp,
    )
}

@Composable
fun DialogoRicarica(onConferma: (Double) -> Unit, onAnnulla: () -> Unit) {
    var importo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Ricarica credito virtuale") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100, 500, 1000).forEach { v -> AssistChip(onClick = { importo = v.toString() }, label = { Text("$v €") }) }
                }
                CampoImporto(importo, { importo = it }, "Importo")
            }
        },
        confirmButton = { TextButton(onClick = { leggiImporto(importo)?.let(onConferma) }, enabled = leggiImporto(importo) != null) { Text("Ricarica", color = Colori.Verde) } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } },
        containerColor = Colori.Superficie,
        tonalElevation = 0.dp,
    )
}

@Composable
fun PrimoAvvio(vm: MatchMindViewModel) {
    var credito by remember { mutableStateOf("1000") }
    var puntata by remember { mutableStateOf("10") }
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = Colori.Superficie) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.app_logo), contentDescription = "Logo MatchMind", modifier = Modifier.size(110.dp).clip(RoundedCornerShape(24.dp)))
                Row {
                    Text("Benvenuto in Match", style = MaterialTheme.typography.titleLarge)
                    Text("Mind", style = MaterialTheme.typography.titleLarge, color = Colori.Verde)
                }
                Text("Scegli il credito virtuale iniziale e la puntata per schedina. Potrai ricaricarlo o azzerarlo quando vuoi.",
                    textAlign = TextAlign.Center, color = Colori.Tenue)
                CampoImporto(credito, { credito = it }, "Credito iniziale")
                CampoImporto(puntata, { puntata = it }, "Puntata per schedina")
                Button(
                    onClick = {
                        val c = leggiImporto(credito)
                        val p = leggiImporto(puntata)
                        if (c != null && p != null) vm.configura(c, p)
                    },
                    enabled = leggiImporto(credito) != null && leggiImporto(puntata) != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Colori.Verde, contentColor = Colori.Sfondo),
                ) { Text("Inizia", fontWeight = FontWeight.Bold) }
                Text("Solo credito virtuale · 18+ · © Roberto Di Flumeri", style = MaterialTheme.typography.labelSmall, color = Colori.Grigio)
            }
        }
    }
}

@Composable
fun DialogoProposta(vm: MatchMindViewModel, proposta: Repository.Proposta) {
    val pref by vm.preferenze.collectAsState()
    val movimenti by vm.movimenti.collectAsState()
    val saldo = Contabilita.saldo(movimenti)
    val s = proposta.schedina
    var puntata by remember(s) { mutableStateOf((pref?.puntata ?: 10.0).toString().removeSuffix(".0").replace('.', ',')) }
    val importo = leggiImporto(puntata)
    val colore = if (s.sport == Sport.CALCIO) Colori.Verde else Colori.Arancio

    Dialog(onDismissRequest = vm::annullaProposta, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Colori.Sfondo, modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Schedina ${s.sport.etichetta}", style = MaterialTheme.typography.headlineSmall, color = colore)
                    Text("${s.pronostici.size} pronostici scelti tra ${proposta.partiteAnalizzate} partite reali analizzate (${proposta.partiteConQuote} con quote) · strategia ${s.strategia.etichetta}",
                        style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                    if (proposta.esperienza.osservazioni > 0)
                        Text("Esperienza applicata: ${proposta.esperienza.osservazioni} partite già verificate", style = MaterialTheme.typography.bodyMedium, color = colore)
                    val soglia = pref?.sicurezza ?: com.matchmind.app.dominio.Generatore.SICUREZZA_PREDEFINITA
                    Text(buildString {
                        append("MatchMind ha scelto ${s.pronostici.size} ${if (s.pronostici.size == 1) "partita" else "partite"} ")
                        append("tra ${proposta.pronosticiSicuri} con pronostici sicuri: ")
                        append(if (s.pronostici.size >= com.matchmind.app.dominio.Generatore.MAX_PARTITE) "raggiunto il massimo di partite."
                        else "aggiungerne altre porterebbe la probabilità di vincere sotto il ${soglia.percentuale()}.")
                    }, style = MaterialTheme.typography.bodyMedium, color = colore)
                }
                items(s.pronostici) { p ->
                    Scheda {
                        Etichetta("${Leghe.nome(p.evento.lega)} · ${p.evento.inizioMillis.dataOra()}")
                        Text("${p.evento.casa} – ${p.evento.ospite}", style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.scelta.descrivi(p.evento.casa, p.evento.ospite),
                                color = colore, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Pillola(p.quota.quota() + if (p.quotaStimata) "*" else "", Colori.Testo)
                        }
                        Etichetta(p.scelta.mercato.etichetta, colore)
                        Text("Probabilità ${p.probabilita.percentuale()}" +
                            (p.pMercato?.let { " · quote ${it.percentuale()}" } ?: "") +
                            (p.pModello?.let { " · statistica ${it.percentuale()}" } ?: ""),
                            style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                    }
                }
                item {
                    Scheda(bordo = colore) {
                        RigaValori(
                            Triple("Quota totale", s.quotaTotale.quota(), Colori.Testo),
                            Triple("Prob. vincerla", s.probabilita.percentualeFine(), colore),
                            Triple("Vincita", importo?.let { (it * s.quotaTotale).euro() } ?: "–", Colori.Verde),
                        )
                        if (s.pronostici.any { it.quotaStimata }) Text("* quota stimata: partita non quotata dal bookmaker", style = MaterialTheme.typography.bodyMedium, color = Colori.Tenue)
                        importo?.let {
                            val ev = s.valoreAtteso(it)
                            Text("Valore atteso: ${ev.euroConSegno()} per schedina (media nel lungo periodo secondo le probabilità stimate)",
                                style = MaterialTheme.typography.bodyMedium, color = if (ev >= 0) Colori.Verde else Colori.Arancio)
                        }
                        CampoImporto(puntata, { puntata = it }, "Puntata")
                        if (importo != null && importo > saldo) Text("Il saldo (${saldo.euro()}) non basta: andrà in debito.", color = Colori.Rosso, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { importo?.let(vm::gioca) }, enabled = importo != null, modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = colore, contentColor = Colori.Sfondo)) {
                            Text("Gioca ${importo?.euro() ?: ""}", fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { vm.annullaProposta(); vm.genera(s.sport) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Rigenera") }
                            OutlinedButton(onClick = vm::annullaProposta, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Annulla", color = Colori.Tenue) }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payrecap.app.dominio.Analisi
import com.payrecap.app.dominio.Avviso
import com.payrecap.app.dominio.Cedolino
import com.payrecap.app.dominio.Confronto
import com.payrecap.app.dominio.Gravita
import com.payrecap.app.dominio.Impostazioni
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun SchermataRiepilogo(
    cedolini: List<Cedolino>,
    selezionatoId: Long?,
    impostazioni: Impostazioni,
    padding: PaddingValues,
    onCarica: () -> Unit,
    onEsempio: () -> Unit,
    onSeleziona: (Long) -> Unit,
    onModifica: (Cedolino) -> Unit,
    onElimina: (Cedolino) -> Unit,
) {
    val c = cedolini.firstOrNull { it.id == selezionatoId } ?: cedolini.firstOrNull()
    if (c == null) {
        StatoVuoto(padding, onCarica, onEsempio)
        return
    }
    val oggi = LocalDate.now()
    val prec = Analisi.precedente(c, cedolini)
    val avvisi = Analisi.avvisi(c, prec, oggi, impostazioni)
    val ordinati = cedolini.sortedBy { it.chiavePeriodo }
    val indice = ordinati.indexOf(c)
    var confermaElimina by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = SpaziaturaElenco,
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Colori.BluChiaro), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Description, null, tint = Colori.Navy, modifier = Modifier.size(28.dp))
                    Icon(Icons.Filled.CheckCircle, null, tint = Colori.Verde,
                        modifier = Modifier.size(16.dp).align(Alignment.BottomEnd).background(Color.White, CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Riepilogo Cedolino", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = Colori.Testo)
                    Text("Cedolino di ${c.etichettaPeriodo}", fontSize = 13.sp, color = Colori.TestoSecondario)
                    if (!c.azienda.isNullOrBlank()) Text(c.azienda, fontSize = 12.sp, color = Colori.TestoSecondario, maxLines = 1)
                }
                IconButton(onClick = { if (indice > 0) onSeleziona(ordinati[indice - 1].id) }, enabled = indice > 0) {
                    Icon(Icons.Filled.ChevronLeft, "Cedolino precedente")
                }
                IconButton(onClick = { if (indice < ordinati.lastIndex) onSeleziona(ordinati[indice + 1].id) }, enabled = indice < ordinati.lastIndex) {
                    Icon(Icons.Filled.ChevronRight, "Cedolino successivo")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tessera(Modifier.weight(1f), Icons.Filled.Payments, "Lordo effettivo", euro(c.lordo), Colori.Navy, Colori.BluChiaro)
                Tessera(Modifier.weight(1f), Icons.Filled.Percent, "Tasse e contributi", euro(c.totaleTasseEContributi), Colori.Rosso, Colori.RossoChiaro)
                Tessera(Modifier.weight(1f), Icons.Filled.AccountBalanceWallet, "Netto effettivo", euro(c.netto), Colori.Verde, Colori.VerdeChiaro)
            }
        }

        if (avvisi.isNotEmpty()) item { SchedaAvvisi(avvisi) }

        item { SchedaContratto(c, oggi) }
        item { SchedaResidui(c) }
        item { SchedaStraordinario(c) }
        item { SchedaTasse(c) }
        if (prec != null) item { SchedaConfronto(Analisi.confronto(c, prec), prec) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onModifica(c) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Correggi dati")
                }
                OutlinedButton(onClick = { confermaElimina = true }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Colori.Rosso)) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Elimina")
                }
            }
        }
        item { Copyright() }
    }

    if (confermaElimina) {
        AlertDialog(
            onDismissRequest = { confermaElimina = false },
            title = { Text("Eliminare il cedolino?") },
            text = { Text("Il riepilogo di ${c.etichettaPeriodo} verrà cancellato da questo telefono.") },
            confirmButton = { TextButton(onClick = { confermaElimina = false; onElimina(c) }) { Text("Elimina", color = Colori.Rosso) } },
            dismissButton = { TextButton(onClick = { confermaElimina = false }) { Text("Annulla") } },
        )
    }
}

// ------------------------------------------------------------------ stato vuoto

@Composable
private fun StatoVuoto(padding: PaddingValues, onCarica: () -> Unit, onEsempio: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF2F74F5), Colori.Navy)))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.payrecap.app.R.drawable.logo_payrecap), null, Modifier.size(84.dp),
                )
                SpazioVerticale()
                Text("Il tuo cedolino,\nin un colpo d'occhio.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center, lineHeight = 28.sp)
                SpazioVerticale(6.dp)
                Text("Carica la busta paga in PDF o una foto: PayRecap estrae lordo, netto, tasse, contratto, ferie, ROL e straordinari.",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center)
                SpazioVerticale(16.dp)
                Button(onClick = onCarica, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Colori.Navy),
                    modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Filled.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Carica il cedolino", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onEsempio) {
                    Icon(Icons.Filled.Science, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("Prova con due cedolini di esempio", color = Color.White)
                }
            }
        }
        item { Funzione(Icons.Filled.UploadFile, "Carica il cedolino", "PDF (anche protetto da password) o immagine") }
        item { Funzione(Icons.Filled.Calculate, "Riepilogo economico", "Lordo, netto, IRPEF, addizionali e contributi") }
        item { Funzione(Icons.Filled.WorkOutline, "Dettagli contratto", "Tipo, CCNL, livello e scadenza") }
        item { Funzione(Icons.Filled.DateRange, "Residui sempre aggiornati", "Ferie e ROL maturati, goduti e residui; ore di straordinario") }
        item { Funzione(Icons.Filled.CompareArrows, "Confronti e avvisi", "Variazioni rispetto ai mesi precedenti e anomalie") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.Lock, null, tint = Colori.Verde, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tutto resta sul telefono: nessun invio dati, nessuna connessione.", fontSize = 12.sp, color = Colori.TestoSecondario)
            }
        }
        item { Copyright() }
    }
}

@Composable
private fun Funzione(icona: ImageVector, titolo: String, sottotitolo: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Colori.Navy), contentAlignment = Alignment.Center) {
            Icon(icona, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(titolo, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Colori.Testo)
            Text(sottotitolo, fontSize = 12.sp, color = Colori.TestoSecondario)
        }
    }
}

// ------------------------------------------------------------------ schede

@Composable
private fun SchedaAvvisi(avvisi: List<Avviso>) {
    Scheda(titolo = "Avvisi", icona = Icons.Filled.Warning) {
        avvisi.forEachIndexed { i, a ->
            if (i > 0) Separatore()
            val (icona, colore) = when (a.gravita) {
                Gravita.CRITICO -> Icons.Filled.Error to Colori.Rosso
                Gravita.AVVISO -> Icons.Filled.Warning to Colori.Arancio
                Gravita.INFO -> Icons.Filled.Info to Colori.Blu
            }
            Row(Modifier.padding(vertical = 6.dp)) {
                Icon(icona, null, tint = colore, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(a.titolo, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Colori.Testo)
                    Text(a.dettaglio, fontSize = 12.sp, color = Colori.TestoSecondario, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun SchedaTasse(c: Cedolino) {
    var mostraStima by remember { mutableStateOf(false) }
    Scheda(titolo = "Tasse e contributi", icona = Icons.Filled.ReceiptLong) {
        RigaValore("IRPEF", euro(c.irpef))
        RigaValore("Addizionale regionale", euro(c.addizionaleRegionale))
        RigaValore("Addizionale comunale", euro(c.addizionaleComunale))
        RigaValore("Totale imposte", euro(c.totaleTasse), enfasi = true)
        Separatore()
        RigaValore("Contributi previdenziali", euro(c.contributiPrevidenziali))
        RigaValore("Totale tasse e contributi", euro(c.totaleTasseEContributi), enfasi = true, coloreValore = Colori.Rosso)
        if (c.lordo != null && c.totaleTasseEContributi != null && c.lordo > 0) {
            val incidenza = c.totaleTasseEContributi!! / c.lordo * 100
            RigaValore("Incidenza sul lordo", "%.1f %%".format(java.util.Locale.ITALY, incidenza))
        }
        if (c.imponibileFiscale != null) RigaValore("Imponibile fiscale", euro(c.imponibileFiscale))
        Text("Valori estratti dal cedolino.", fontSize = 11.sp, color = Colori.TestoSecondario)

        val stima = Analisi.stimaIrpef(c)
        if (stima != null) {
            SpazioVerticale(8.dp)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Colori.ArancioChiaro).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pillola("STIMA", Colori.Arancio, Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("IRPEF mensile stimata: ${euro(stima.irpefMensileStimata)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                }
                if (mostraStima) {
                    SpazioVerticale(6.dp)
                    RigaValore("Imponibile annuo ipotizzato (×12)", euro(stima.imponibileAnnuo))
                    RigaValore("IRPEF lorda annua (scaglioni 2026)", euro(stima.irpefLordaAnnua))
                    RigaValore("Detrazione lavoro dipendente", euro(stima.detrazioneLavoroAnnua))
                    stima.differenza?.let { RigaValore("Differenza busta − stima", euro(it)) }
                }
                Text(
                    "Stima indicativa: il ricalcolo dipende da contratto, imponibile progressivo, detrazioni per familiari, " +
                        "cuneo fiscale e conguagli. Fa fede il cedolino.",
                    fontSize = 11.sp, color = Colori.TestoSecondario, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp),
                )
                TextButton(onClick = { mostraStima = !mostraStima }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (mostraStima) "Nascondi calcolo" else "Mostra calcolo", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SchedaContratto(c: Cedolino, oggi: LocalDate) {
    Scheda(titolo = "Contratto", icona = Icons.Filled.WorkOutline) {
        RigaValore("Tipo contratto", c.tipoContratto ?: "—")
        RigaValore("CCNL", c.ccnl ?: "—")
        RigaValore("Livello", c.livello?.let { if (it.endsWith("°")) "$it Livello" else it } ?: "—")
        val scad = Analisi.parseData(c.scadenza)
        val nota = scad?.let {
            val g = ChronoUnit.DAYS.between(oggi, it)
            if (g < 0) "scaduto da ${-g} giorni" else "tra $g giorni"
        }
        RigaValore("Scadenza", c.scadenza ?: "—", nota = nota,
            coloreValore = if (scad != null && scad.isBefore(oggi.plusDays(60))) Colori.Arancio else Colori.Testo)
    }
}

@Composable
private fun SchedaResidui(c: Cedolino) {
    Scheda(titolo = "Residui", icona = Icons.Filled.DateRange) {
        RigaResiduo(Icons.Filled.BeachAccess, "Ferie", c.ferieMaturate, c.ferieGodute, c.ferieResidue, c.unitaFerie, Colori.Verde)
        Separatore()
        RigaResiduo(Icons.Filled.AccessTime, "ROL / Permessi", c.rolMaturati, c.rolGoduti, c.rolResidui, c.unitaRol, Colori.Blu)
    }
}

@Composable
private fun RigaResiduo(icona: ImageVector, nome: String, mat: Double?, god: Double?, res: Double?, unita: String, colore: Color) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icona, null, tint = colore, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(nome, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp))
            val frazione = if (res != null && mat != null && mat > 0) (res / mat).toFloat() else if (res != null) 1f else 0f
            BarraProgresso(frazione, colore, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(res?.let { "${numero(it)} $unita" } ?: "—", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        Text(
            "Maturati ${numero(mat)} · Goduti ${numero(god)} · Residui ${numero(res)} $unita",
            fontSize = 11.sp, color = Colori.TestoSecondario, modifier = Modifier.padding(start = 28.dp, top = 2.dp),
        )
    }
}

@Composable
private fun SchedaStraordinario(c: Cedolino) {
    Scheda(titolo = "Straordinario", icona = Icons.Filled.Timer) {
        val eff = c.straordinarioEffettuate
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Icon(Icons.Filled.MoreTime, null, tint = Colori.Viola, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Ore effettuate", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp))
            val frazione = if (eff != null && eff > 0) (((c.straordinarioPagate ?: 0.0) + (c.straordinarioAccantonate ?: 0.0)) / eff).toFloat() else 0f
            BarraProgresso(frazione, Colori.Viola, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(eff?.let { "${numero(it)} ore" } ?: "—", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        RigaValore("Ore pagate in busta", c.straordinarioPagate?.let { "${numero(it)} ore" } ?: "—")
        RigaValore("Ore accantonate", c.straordinarioAccantonate?.let { "${numero(it)} ore" } ?: "—")
        RigaValore("Banca ore residua", c.bancaOreResidua?.let { "${numero(it)} ore" } ?: "—", enfasi = true)
        Text("Lo straordinario non è un residuo come ferie e ROL: le ore si pagano nel mese o, se previsto, finiscono in banca ore.",
            fontSize = 11.sp, color = Colori.TestoSecondario, lineHeight = 15.sp)
    }
}

@Composable
private fun SchedaConfronto(voci: List<Confronto>, prec: Cedolino) {
    Scheda(titolo = "Confronto con ${prec.etichettaPeriodo}", icona = Icons.Filled.CompareArrows) {
        voci.filter { it.attuale != null || it.precedente != null }.forEachIndexed { i, v ->
            if (i > 0) Separatore()
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(v.voce, fontSize = 14.sp, color = Colori.Testo)
                    Text("prima: ${formatta(v.precedente, v.unita)}", fontSize = 11.sp, color = Colori.TestoSecondario)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatta(v.attuale, v.unita), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    val d = v.delta
                    if (d != null && kotlin.math.abs(d) >= 0.005) {
                        // per tasse più alto = peggio; per le altre voci più alto = meglio
                        val buono = if (v.voce.startsWith("Tasse") || v.voce == "IRPEF") d < 0 else d > 0
                        val colore = if (buono) Colori.Verde else Colori.Rosso
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (d > 0) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, null,
                                tint = colore, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            val perc = v.deltaPercentuale?.let { " (%+.1f%%)".format(java.util.Locale.ITALY, it) } ?: ""
                            Text((if (d > 0) "+" else "") + formatta(d, v.unita) + perc, fontSize = 11.sp, color = colore)
                        }
                    } else {
                        Text("invariato", fontSize = 11.sp, color = Colori.TestoSecondario)
                    }
                }
            }
        }
    }
}

private fun formatta(v: Double?, unita: String): String = when {
    v == null -> "—"
    unita == "€" -> euro(v)
    else -> "${numero(v)} $unita"
}

// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payrecap.app.dominio.Cedolino
import com.payrecap.app.dominio.ParserCedolino
import com.payrecap.app.dominio.nomeMese
import java.util.Locale

/** Stato modificabile del modulo: un testo per campo. */
private class Modulo(c: Cedolino) {
    private fun n(v: Double?) = mutableStateOf(v?.let { if (it % 1.0 == 0.0) "%.0f".format(Locale.ITALY, it) else "%.2f".format(Locale.ITALY, it) } ?: "")
    private fun t(v: String?) = mutableStateOf(v ?: "")

    val mese = mutableStateOf(c.mese)
    val anno = mutableStateOf(c.anno.toString())
    val aggiuntiva = mutableStateOf(c.aggiuntiva)
    val azienda = t(c.azienda)
    val lordo = n(c.lordo); val netto = n(c.netto); val imponibile = n(c.imponibileFiscale)
    val irpef = n(c.irpef); val addReg = n(c.addizionaleRegionale); val addCom = n(c.addizionaleComunale); val contributi = n(c.contributiPrevidenziali)
    val tipo = t(c.tipoContratto); val ccnl = t(c.ccnl); val livello = t(c.livello); val scadenza = t(c.scadenza)
    val ferieMat = n(c.ferieMaturate); val ferieGod = n(c.ferieGodute); val ferieRes = n(c.ferieResidue); val unitaFerie = mutableStateOf(c.unitaFerie)
    val rolMat = n(c.rolMaturati); val rolGod = n(c.rolGoduti); val rolRes = n(c.rolResidui); val unitaRol = mutableStateOf(c.unitaRol)
    val strEff = n(c.straordinarioEffettuate); val strPag = n(c.straordinarioPagate); val strAcc = n(c.straordinarioAccantonate); val banca = n(c.bancaOreResidua)

    private fun MutableState<String>.num() = value.trim().takeIf { it.isNotEmpty() }?.let { ParserCedolino.parseNumero(it) }
    private fun MutableState<String>.txt() = value.trim().takeIf { it.isNotEmpty() }

    fun annoValido() = anno.value.toIntOrNull()?.let { it in 1990..2100 } == true

    fun applica(base: Cedolino) = base.copy(
        mese = mese.value, anno = anno.value.toInt(), aggiuntiva = aggiuntiva.value, azienda = azienda.txt(),
        lordo = lordo.num(), netto = netto.num(), imponibileFiscale = imponibile.num(),
        irpef = irpef.num(), addizionaleRegionale = addReg.num(), addizionaleComunale = addCom.num(), contributiPrevidenziali = contributi.num(),
        tipoContratto = tipo.txt(), ccnl = ccnl.txt(), livello = livello.txt(), scadenza = scadenza.txt(),
        ferieMaturate = ferieMat.num(), ferieGodute = ferieGod.num(), ferieResidue = ferieRes.num(), unitaFerie = unitaFerie.value,
        rolMaturati = rolMat.num(), rolGoduti = rolGod.num(), rolResidui = rolRes.num(), unitaRol = unitaRol.value,
        straordinarioEffettuate = strEff.num(), straordinarioPagate = strPag.num(), straordinarioAccantonate = strAcc.num(), bancaOreResidua = banca.num(),
    )
}

@Composable
fun SchermataRevisione(stato: StatoCaricamento.Revisione, onAnnulla: () -> Unit, onSalva: (Cedolino) -> Unit) {
    val m = remember(stato) { Modulo(stato.bozza) }
    var mostraTesto by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Colori.Sfondo).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAnnulla) { Icon(Icons.Filled.Close, "Annulla") }
            Text(if (stato.nuovo) "Controlla i dati letti" else "Correggi il cedolino", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (stato.riconosciuti != null && stato.totali != null) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Colori.BluChiaro).padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Colori.Blu, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Riconosciuti ${stato.riconosciuti} campi su ${stato.totali}", fontWeight = FontWeight.SemiBold, color = Colori.Navy)
                        }
                        BarraProgresso(stato.riconosciuti / stato.totali.toFloat(), Colori.Blu, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        Text(
                            (stato.metodo?.let { "Lettura: $it. " } ?: "") +
                                if (stato.mancanti.isEmpty()) "Controlla comunque i valori prima di salvare."
                                else "Non trovati: ${stato.mancanti.joinToString(", ")}. Puoi completarli a mano se presenti nel cedolino.",
                            fontSize = 12.sp, color = Colori.TestoSecondario, lineHeight = 16.sp,
                        )
                    }
                }
            }

            item {
                Scheda(titolo = "Periodo: ${nomeMese(m.mese.value)} ${m.anno.value}") {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..12).forEach { i ->
                            FilterChip(selected = m.mese.value == i, onClick = { m.mese.value = i }, label = { Text(nomeMese(i).take(3)) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Campo("Anno", m.anno, Modifier.width(120.dp), numerico = true)
                        Spacer(Modifier.width(12.dp))
                        Checkbox(checked = m.aggiuntiva.value, onCheckedChange = { m.aggiuntiva.value = it })
                        Text("Mensilità aggiuntiva (13ª/14ª)", fontSize = 13.sp)
                    }
                    Campo("Azienda", m.azienda)
                }
            }
            item {
                Scheda(titolo = "Importi (€)") {
                    Coppia({ Campo("Lordo", m.lordo, it, true) }, { Campo("Netto", m.netto, it, true) })
                    Campo("Imponibile fiscale", m.imponibile, numerico = true)
                }
            }
            item {
                Scheda(titolo = "Tasse e contributi (€)") {
                    Coppia({ Campo("IRPEF", m.irpef, it, true) }, { Campo("Contributi", m.contributi, it, true) })
                    Coppia({ Campo("Add. regionale", m.addReg, it, true) }, { Campo("Add. comunale", m.addCom, it, true) })
                }
            }
            item {
                Scheda(titolo = "Contratto") {
                    Campo("Tipo contratto", m.tipo)
                    Campo("CCNL", m.ccnl)
                    Coppia({ Campo("Livello", m.livello, it) }, { Campo("Scadenza gg/mm/aaaa", m.scadenza, it) })
                }
            }
            item {
                Scheda(titolo = "Ferie") {
                    Unita(m.unitaFerie)
                    Tripla(m.ferieMat, m.ferieGod, m.ferieRes)
                }
            }
            item {
                Scheda(titolo = "ROL / Permessi") {
                    Unita(m.unitaRol)
                    Tripla(m.rolMat, m.rolGod, m.rolRes)
                }
            }
            item {
                Scheda(titolo = "Straordinario (ore)") {
                    Coppia({ Campo("Effettuate", m.strEff, it, true) }, { Campo("Pagate", m.strPag, it, true) })
                    Coppia({ Campo("Accantonate", m.strAcc, it, true) }, { Campo("Banca ore residua", m.banca, it, true) })
                }
            }
            if (!stato.bozza.testoEstratto.isNullOrBlank()) {
                item {
                    Scheda(titolo = "Testo letto dal documento", azione = {
                        TextButton(onClick = { mostraTesto = !mostraTesto }) { Text(if (mostraTesto) "Nascondi" else "Mostra") }
                    }) {
                        if (mostraTesto) {
                            Text(stato.bozza.testoEstratto!!.take(6000), fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp,
                                modifier = Modifier.horizontalScroll(rememberScrollState()))
                        } else {
                            Text("Utile per verificare cosa è stato riconosciuto.", fontSize = 12.sp, color = Colori.TestoSecondario)
                        }
                    }
                }
            }
        }
        Button(
            onClick = { onSalva(m.applica(stato.bozza)) },
            enabled = m.annoValido(),
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Check, null); Spacer(Modifier.width(8.dp)); Text("Salva riepilogo", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Campo(etichetta: String, stato: MutableState<String>, modifier: Modifier = Modifier.fillMaxWidth(), numerico: Boolean = false) {
    OutlinedTextField(
        value = stato.value,
        onValueChange = { stato.value = it },
        label = { Text(etichetta, fontSize = 12.sp) },
        singleLine = true,
        keyboardOptions = if (numerico) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier.padding(vertical = 3.dp),
    )
}

@Composable
private fun Coppia(a: @Composable (Modifier) -> Unit, b: @Composable (Modifier) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        a(Modifier.weight(1f))
        b(Modifier.weight(1f))
    }
}

@Composable
private fun Tripla(mat: MutableState<String>, god: MutableState<String>, res: MutableState<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Campo("Maturati", mat, Modifier.weight(1f), true)
        Campo("Goduti", god, Modifier.weight(1f), true)
        Campo("Residui", res, Modifier.weight(1f), true)
    }
}

@Composable
private fun Unita(stato: MutableState<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Unità:", fontSize = 13.sp, color = Colori.TestoSecondario)
        listOf(Cedolino.UNITA_GIORNI, Cedolino.UNITA_ORE).forEach { u ->
            FilterChip(selected = stato.value == u, onClick = { stato.value = u }, label = { Text(u) })
        }
    }
    Spacer(Modifier.height(4.dp))
}

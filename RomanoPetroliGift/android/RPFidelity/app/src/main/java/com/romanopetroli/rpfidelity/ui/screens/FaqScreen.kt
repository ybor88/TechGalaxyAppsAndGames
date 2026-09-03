package com.romanopetroli.rpfidelity.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.ui.theme.RpTextDark

private data class Domanda(val q: String, val a: String)

private val domande = listOf(
    Domanda(
        "Come si accumulano i punti?",
        "Ogni volta che fai rifornimento presso il nostro distributore, maturi 1 punto ogni 10 euro spesi " +
            "(es. 45€ di rifornimento = 4,50 punti). I punti vengono accreditati automaticamente sul tuo saldo dopo ogni rifornimento."
    ),
    Domanda(
        "Come riscatto un voucher?",
        "Vai nella sezione \"Voucher\": ogni premio del catalogo mostra i punti necessari per ottenerlo. " +
            "Se hai punti a sufficienza puoi premere \"Riscatta\" e il voucher verrà aggiunto a \"I miei voucher\"."
    ),
    Domanda(
        "Dove trovo i voucher che ho già riscattato?",
        "Nella sezione \"Voucher\" trovi tutti i tuoi buoni con il relativo codice QR, la scadenza e lo stato (attivo, usato o scaduto)."
    ),
    Domanda(
        "Come uso un voucher al distributore?",
        "Mostra il codice QR del voucher al gestore al momento del rifornimento: verrà scalato dall'importo da pagare. " +
            "Puoi applicare più voucher allo stesso rifornimento, purché il loro valore complessivo non superi l'importo del rifornimento."
    ),
    Domanda(
        "I voucher scadono?",
        "Sì, ogni voucher riscattato ha una data di scadenza indicata nella sezione \"Voucher\". Ti consigliamo di utilizzarlo prima di quella data."
    ),
    Domanda(
        "Come mostro la mia Card per accumulare punti?",
        "Nella sezione \"La mia Card\" trovi il tuo QR personale: mostralo al gestore ad ogni rifornimento per far accreditare i punti sul tuo account."
    ),
    Domanda(
        "Come modifico i miei dati personali o la password?",
        "Vai in \"Impostazioni\": da lì puoi aggiornare nome, cognome, email e telefono, oppure cambiare la password del tuo account."
    ),
    Domanda(
        "Non trovo risposta alla mia domanda, cosa faccio?",
        "Scrivici dalla sezione \"Contatti\": ti risponderemo il prima possibile."
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onOpenDrawer: () -> Unit) {
    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "FAQ",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onOpenDrawer,
                navigationContentDescription = "Apri menu"
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            items(domande) { d -> FaqCard(d) }
        }
    }
}

@Composable
private fun FaqCard(domanda: Domanda) {
    var espansa by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { espansa = !espansa }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(domanda.q, fontWeight = FontWeight.Bold, color = RpTextDark)
            if (espansa) {
                Text(domanda.a, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

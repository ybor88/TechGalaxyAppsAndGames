// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.payrecap.app.R
import com.payrecap.app.dominio.Analisi

fun euro(v: Double?): String = v?.let { Analisi.euro(it) } ?: "—"
fun numero(v: Double?): String = v?.let { Analisi.num(it) } ?: "—"

@Composable
fun Logo(dimensione: Dp = 34.dp, testo: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo_payrecap), contentDescription = "PayRecap", modifier = Modifier.size(dimensione))
        if (testo) {
            Spacer(Modifier.width(8.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Colori.Navy, fontWeight = FontWeight.ExtraBold)) { append("Pay") }
                    withStyle(SpanStyle(color = Colori.Blu, fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic)) { append("Recap") }
                },
                fontSize = (dimensione.value * 0.66f).sp,
            )
        }
    }
}

@Composable
fun Scheda(
    modifier: Modifier = Modifier,
    titolo: String? = null,
    icona: ImageVector? = null,
    azione: (@Composable RowScope.() -> Unit)? = null,
    contenuto: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Colori.Card)
            .border(1.dp, Colori.Bordo, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        if (titolo != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (icona != null) {
                    Icon(icona, null, tint = Colori.Navy, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(titolo, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Colori.Testo, modifier = Modifier.weight(1f))
                azione?.invoke(this)
            }
            Spacer(Modifier.height(12.dp))
        }
        contenuto()
    }
}

/** Le tre tessere colorate in cima al riepilogo (lordo, tasse, netto). */
@Composable
fun Tessera(modifier: Modifier, icona: ImageVector, etichetta: String, valore: String, colore: Color, sfondo: Color) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(sfondo)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
            Icon(icona, null, tint = colore, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(etichetta, fontSize = 11.sp, color = Colori.TestoSecondario, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(valore, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = colore, maxLines = 1)
    }
}

@Composable
fun RigaValore(etichetta: String, valore: String, enfasi: Boolean = false, coloreValore: Color = Colori.Testo, nota: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(etichetta, fontSize = 14.sp, color = if (enfasi) Colori.Testo else Colori.TestoSecondario,
                fontWeight = if (enfasi) FontWeight.SemiBold else FontWeight.Normal)
            if (nota != null) Text(nota, fontSize = 11.sp, color = Colori.TestoSecondario)
        }
        Spacer(Modifier.width(12.dp))
        Text(valore, fontSize = 14.sp, fontWeight = if (enfasi) FontWeight.ExtraBold else FontWeight.SemiBold, color = coloreValore)
    }
}

@Composable
fun Separatore() {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Colori.Bordo))
}

@Composable
fun BarraProgresso(frazione: Float, colore: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(8.dp).clip(RoundedCornerShape(50)).background(Colori.Traccia)) {
        Box(Modifier.fillMaxWidth(frazione.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(50)).background(colore))
    }
}

@Composable
fun Pillola(testo: String, colore: Color, sfondo: Color) {
    Text(
        testo,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = colore,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(sfondo).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Copyright() {
    Text(
        "© Roberto Di Flumeri",
        fontSize = 11.sp,
        color = Colori.TestoSecondario,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
fun SpazioVerticale(h: Dp = 12.dp) = Spacer(Modifier.height(h))

val SpaziaturaElenco = Arrangement.spacedBy(12.dp)

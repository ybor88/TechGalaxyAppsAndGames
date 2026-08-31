// Copyright (c) Roberto Di Flumeri
package com.volcanoescape.app.ui.screens.monitoring

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.volcanoescape.app.data.model.SeismicEvent
import com.volcanoescape.app.data.model.Volcano
import com.volcanoescape.app.data.repository.DailyActivityPoint
import com.volcanoescape.app.data.repository.SeismicAlertLevel
import com.volcanoescape.app.data.repository.SeismicRiskAssessment
import com.volcanoescape.app.data.repository.SeismicTrend
import com.volcanoescape.app.ui.theme.EruptionRed
import com.volcanoescape.app.ui.theme.LavaOrange
import com.volcanoescape.app.ui.theme.MeadowGreen
import com.volcanoescape.app.ui.theme.SunsetAmber
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(
    volcano: Volcano,
    viewModel: MonitoringViewModel,
    onBack: () -> Unit,
    onFindEscapeRoute: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(volcano.displayName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    titleContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            Button(
                onClick = onFindEscapeRoute,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EruptionRed, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Text("Trova la via di fuga meno trafficata", fontWeight = FontWeight.Bold)
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DaysFilterRow(
                selectedDays = uiState.selectedDays,
                onDaysSelected = viewModel::onDaysSelected,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    uiState.errorMessage != null -> Text(
                        text = "Impossibile scaricare i dati INGV: ${uiState.errorMessage}",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    uiState.events.isEmpty() -> Text(
                        text = "Nessuna scossa registrata nell'area ${periodLabel(uiState.selectedDays)}.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        uiState.riskAssessment?.let { assessment ->
                            item { RiskAssessmentCard(assessment) }
                            item { ActivityChartCard(assessment.dailyActivity) }
                        }
                        item {
                            Text(
                                text = "Ultime scosse registrate (dati INGV) ${periodLabel(uiState.selectedDays)}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                        items(uiState.events) { event -> SeismicEventRow(event) }
                    }
                }
            }
        }
    }
}

private val seismicDaysFilterOptions = listOf(7L to "7 giorni", 30L to "30 giorni", 90L to "90 giorni", 365L to "1 anno")

private fun periodLabel(days: Long): String = when (days) {
    365L -> "nell'ultimo anno"
    else -> "negli ultimi $days giorni"
}

@Composable
private fun DaysFilterRow(selectedDays: Long, onDaysSelected: (Long) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        seismicDaysFilterOptions.forEach { (days, label) ->
            FilterChip(
                selected = days == selectedDays,
                onClick = { onDaysSelected(days) },
                label = { Text(label) },
            )
        }
    }
}

private fun levelColor(level: SeismicAlertLevel): Color = when (level) {
    SeismicAlertLevel.VERDE -> MeadowGreen
    SeismicAlertLevel.GIALLO -> SunsetAmber
    SeismicAlertLevel.ARANCIONE -> LavaOrange
    SeismicAlertLevel.ROSSO -> EruptionRed
}

private fun trendArrow(trend: SeismicTrend): String = when (trend) {
    SeismicTrend.IN_CRESCITA -> "↑"
    SeismicTrend.IN_CALO -> "↓"
    SeismicTrend.STABILE -> "→"
}

@Composable
private fun RiskAssessmentCard(assessment: SeismicRiskAssessment) {
    val color = levelColor(assessment.level)
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(50), color = color) {
                    Text(
                        text = assessment.level.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                Text(
                    text = assessment.level.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 10.dp),
                )
                Text(
                    text = "  ${trendArrow(assessment.trend)} ${assessment.trend.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }

            Text(
                text = "Indice di attività sismica stimato: ${assessment.score}/100",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            LinearProgressIndicator(
                progress = { assessment.score / 100f },
                color = color,
                trackColor = color.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )

            Text(
                text = "${assessment.eventsLast7Days} scosse negli ultimi 7 giorni " +
                    "(${assessment.eventsLast30Days} negli ultimi 30) · " +
                    "magnitudo massima recente ${"%.1f".format(assessment.maxMagnitudeLast7Days)}" +
                    if (assessment.isShallowing) " · ipocentri in risalita" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(modifier = Modifier.padding(top = 10.dp)) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    text = "Stima non ufficiale calcolata solo dalla sismicità (frequenza, energia, " +
                        "magnitudo e profondità delle scosse). Non è il livello di allerta INGV/Protezione " +
                        "Civile: fai sempre riferimento al bollettino ufficiale del vulcano.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun ActivityChartCard(dailyActivity: List<DailyActivityPoint>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Andamento scosse — ultimi 30 giorni",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            DailyActivityChart(
                dailyActivity = dailyActivity,
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun DailyActivityChart(dailyActivity: List<DailyActivityPoint>, modifier: Modifier = Modifier) {
    val maxCount = (dailyActivity.maxOfOrNull { it.eventCount } ?: 0).coerceAtLeast(1)
    val barColor = LavaOrange

    Canvas(modifier = modifier) {
        if (dailyActivity.isEmpty()) return@Canvas
        val barSlotWidth = size.width / dailyActivity.size
        val barWidth = barSlotWidth * 0.6f

        dailyActivity.forEachIndexed { index, point ->
            val barHeight = (point.eventCount.toFloat() / maxCount) * size.height
            val left = index * barSlotWidth + (barSlotWidth - barWidth) / 2f
            val color = if (point.maxMagnitude >= 3.0) EruptionRed else barColor
            drawRect(
                color = color,
                topLeft = Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(1.5f)),
            )
        }

        // Linea di base.
        drawLine(
            color = Color.Gray.copy(alpha = 0.4f),
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 2f,
        )
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
private fun SeismicEventRow(event: SeismicEvent) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = severityColor(event.magnitude).copy(alpha = 0.08f),
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = event.locationName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = event.time.format(timeFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Text(
                    text = "Profondità ${"%.1f".format(event.depthKm)} km",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            MagnitudeBadge(magnitudeType = event.magnitudeType, magnitude = event.magnitude)
        }
    }
}

private fun severityColor(magnitude: Double): Color = when {
    magnitude < 2.0 -> MeadowGreen
    magnitude < 3.5 -> SunsetAmber
    else -> EruptionRed
}

@Composable
private fun MagnitudeBadge(magnitudeType: String, magnitude: Double) {
    Surface(
        shape = RoundedCornerShape(50),
        color = severityColor(magnitude),
    ) {
        Text(
            text = "$magnitudeType ${"%.1f".format(magnitude)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

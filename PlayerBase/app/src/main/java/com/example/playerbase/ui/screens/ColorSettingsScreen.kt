package com.example.playerbase.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.playerbase.data.AvatarPalette
import com.example.playerbase.ui.theme.BrandColors
import com.example.playerbase.ui.theme.ThemeColorStore

/**
 * Pannello per personalizzare i colori principali del brand (blu, oro, accento
 * basket, accento calcio): applicati subito a intestazioni, card della Home e
 * maglie, e salvati in modo permanente sul dispositivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Personalizza colori") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        BrandColors.reset()
                        ThemeColorStore.save(context)
                    }) {
                        Icon(Icons.Filled.Restore, contentDescription = "Ripristina colori predefiniti")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BrandColors.navy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                "Scegli i colori principali dell'app: cambiano subito intestazioni, card della Home e maglie.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(24.dp))

            ColorRoleSection(
                label = "Blu principale (intestazioni)",
                selected = BrandColors.navy,
                onSelect = { c -> BrandColors.navy = c; ThemeColorStore.save(context) }
            )
            Spacer(modifier = Modifier.height(24.dp))

            ColorRoleSection(
                label = "Oro / accento statistiche",
                selected = BrandColors.gold,
                onSelect = { c -> BrandColors.gold = c; ThemeColorStore.save(context) }
            )
            Spacer(modifier = Modifier.height(24.dp))

            ColorRoleSection(
                label = "Colore Basket",
                selected = BrandColors.basket,
                onSelect = { c -> BrandColors.basket = c; ThemeColorStore.save(context) }
            )
            Spacer(modifier = Modifier.height(24.dp))

            ColorRoleSection(
                label = "Colore Calcio",
                selected = BrandColors.calcio,
                onSelect = { c -> BrandColors.calcio = c; ThemeColorStore.save(context) }
            )
        }
    }
}

@Composable
private fun ColorRoleSection(label: String, selected: Color, onSelect: (Color) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth()) {
        AvatarPalette.themeColors.forEach { colorLong ->
            val color = Color(colorLong)
            val isSelected = color == selected
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0x33000000),
                        shape = CircleShape
                    )
                    .clickable { onSelect(color) }
            )
        }
    }
}

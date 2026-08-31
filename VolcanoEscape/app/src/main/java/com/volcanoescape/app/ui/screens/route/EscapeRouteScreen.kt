// Copyright (c) Roberto Di Flumeri
package com.volcanoescape.app.ui.screens.route

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.volcanoescape.app.data.model.EscapeRoute
import com.volcanoescape.app.data.model.GeoPoint
import com.volcanoescape.app.data.model.Volcano
import com.volcanoescape.app.ui.theme.EruptionRed
import com.volcanoescape.app.ui.theme.LavaOrange
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscapeRouteScreen(
    volcano: Volcano,
    viewModel: EscapeRouteViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.loadEscapeRoute()
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Via di fuga · ${volcano.displayName}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = EruptionRed,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            EscapeRouteMap(
                volcano = volcano,
                userLocation = uiState.userLocation,
                route = uiState.routeOptions?.best,
                modifier = Modifier.fillMaxSize(),
            )

            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.errorMessage != null -> Surface(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                uiState.routeOptions != null -> RouteSummaryCard(uiState.routeOptions!!.best) {
                    uiState.routeOptions!!.best.points.lastOrNull()?.let { destination ->
                        openExternalNavigation(context, destination)
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteSummaryCard(route: EscapeRoute, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .background(Brush.horizontalGradient(colors = listOf(LavaOrange, EruptionRed)))
                .padding(18.dp),
        ) {
            Text(
                "Percorso meno trafficato",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            val minutes = route.travelTimeSeconds / 60
            val delayMinutes = route.trafficDelaySeconds / 60
            val km = route.lengthMeters / 1000.0
            Text("Tempo stimato: $minutes min (ritardo da traffico: $delayMinutes min)", color = Color.White)
            Text("Distanza: ${"%.1f".format(km)} km", color = Color.White)
            Text(
                "Tocca per avviare la navigazione",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Apre Google Maps (o, se non installato, il browser su Google Maps) con la navigazione
 * auto già avviata verso [destination].
 */
private fun openExternalNavigation(context: Context, destination: GeoPoint) {
    val gmmIntentUri = Uri.parse(
        "google.navigation:q=${destination.latitude},${destination.longitude}&mode=d",
    )
    val mapsIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
        setPackage("com.google.android.apps.maps")
    }
    if (mapsIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(mapsIntent)
        return
    }

    val fallbackUri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1" +
            "&destination=${destination.latitude},${destination.longitude}&travelmode=driving",
    )
    context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
}

@Composable
private fun EscapeRouteMap(
    volcano: Volcano,
    userLocation: GeoPoint?,
    route: EscapeRoute?,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(11.0)
            controller.setCenter(OsmGeoPoint(volcano.latitude, volcano.longitude))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier) { map ->
        map.overlays.clear()

        map.overlays.add(
            Marker(map).apply {
                position = OsmGeoPoint(volcano.latitude, volcano.longitude)
                title = volcano.displayName
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            },
        )

        userLocation?.let { location ->
            map.overlays.add(
                Marker(map).apply {
                    position = OsmGeoPoint(location.latitude, location.longitude)
                    title = "La tua posizione"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                },
            )
        }

        route?.let { escapeRoute ->
            val polyline = Polyline().apply {
                setPoints(escapeRoute.points.map { OsmGeoPoint(it.latitude, it.longitude) })
                outlinePaint.strokeWidth = 10f
                outlinePaint.color = android.graphics.Color.parseColor("#FF6A1A")
            }
            map.overlays.add(polyline)

            escapeRoute.points.lastOrNull()?.let { destination ->
                map.overlays.add(
                    Marker(map).apply {
                        position = OsmGeoPoint(destination.latitude, destination.longitude)
                        title = "Punto sicuro"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    },
                )
            }
            map.controller.setCenter(OsmGeoPoint(escapeRoute.points[escapeRoute.points.size / 2].latitude, escapeRoute.points[escapeRoute.points.size / 2].longitude))
        }

        map.invalidate()
    }
}

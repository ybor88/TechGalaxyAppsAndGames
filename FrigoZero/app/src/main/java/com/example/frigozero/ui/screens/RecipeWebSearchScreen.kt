package com.example.frigozero.ui.screens

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.example.frigozero.viewmodel.FrigoViewModel
import java.net.URLEncoder

/**
 * Ricerca ricette "vera": apre una WebView su una ricerca web reale con gli
 * ingredienti selezionati, invece di un catalogo fisso con risultati limitati.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeWebSearchScreen(
    viewModel: FrigoViewModel,
    onBack: () -> Unit
) {
    val scannedIngredients by viewModel.scannedIngredients.collectAsState()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    val searchUrl = remember(scannedIngredients) {
        val query = if (scannedIngredients.isEmpty()) {
            "ricette di cucina"
        } else {
            "ricette con " + scannedIngredients.joinToString(", ")
        }
        "https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")
    }

    val goBackOrExit: () -> Unit = {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onBack()
        }
    }

    BackHandler(onBack = goBackOrExit)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cerca ricette sul web") },
                navigationIcon = {
                    IconButton(onClick = goBackOrExit) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            canGoBack = view.canGoBack()
                        }
                    }
                    loadUrl(searchUrl)
                    webView = this
                }
            }
        )
    }
}

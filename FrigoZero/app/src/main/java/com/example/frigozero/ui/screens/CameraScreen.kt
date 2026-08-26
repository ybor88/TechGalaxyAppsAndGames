package com.example.frigozero.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.frigozero.data.OpenFoodFactsDataSource
import com.example.frigozero.viewmodel.FrigoViewModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.Executors

// Evita di rileggere/riaggiungere lo stesso codice a barre a ripetizione
// mentre l'inquadratura resta ferma sul prodotto.
private const val barcodeCooldownMillis = 5000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: FrigoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { findLifecycleOwner(context) }
    val coroutineScope = rememberCoroutineScope()

    if (lifecycleOwner == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Errore: lifecycle non disponibile per la fotocamera")
        }
        return
    }

    var flashMessage by remember { mutableStateOf("📷 Inquadra il codice a barre del prodotto") }
    var manualInput by remember { mutableStateOf("") }

    var lastBarcode by remember { mutableStateOf<String?>(null) }
    var lastBarcodeTimestamp by remember { mutableStateOf(0L) }
    var isLookingUpBarcode by remember { mutableStateOf(false) }

    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128
                )
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    val onBarcodeDetected: (String) -> Unit = onBarcodeDetected@{ code ->
        val now = System.currentTimeMillis()
        val isSameRecentCode = code == lastBarcode && (now - lastBarcodeTimestamp) < barcodeCooldownMillis
        if (isLookingUpBarcode || isSameRecentCode) {
            return@onBarcodeDetected
        }

        lastBarcode = code
        lastBarcodeTimestamp = now
        isLookingUpBarcode = true
        flashMessage = "🔍 Codice trovato, cerco il prodotto..."

        coroutineScope.launch {
            val result = OpenFoodFactsDataSource.lookupByBarcode(code)
            isLookingUpBarcode = false
            if (result != null) {
                val ingredientToAdd = result.canonicalIngredient ?: result.displayName
                viewModel.addIngredient(ingredientToAdd)
                flashMessage = "✅ Aggiunto: ${result.displayName}"
            } else {
                flashMessage = "⚠️ Prodotto non trovato nel database. Aggiungilo manualmente qui sotto."
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scansiona Prodotto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Camera preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        setupCamera(
                            context = ctx,
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,
                            cameraExecutor = cameraExecutor,
                            barcodeScanner = barcodeScanner,
                            onBarcodeDetected = onBarcodeDetected,
                            onCameraReady = { boundCamera, provider ->
                                camera = boundCamera
                                cameraProvider = provider
                            }
                        )
                        previewView
                    }
                )

                // Scan frame overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(140.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // Flash message overlay
                if (flashMessage.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(flashMessage, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // Torcia — utile con poca luce per leggere il barcode.
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    IconButton(
                        onClick = {
                            val newState = !torchOn
                            camera?.cameraControl?.enableTorch(newState)
                            torchOn = newState
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (torchOn) "Spegni flash" else "Accendi flash",
                            tint = Color.White
                        )
                    }
                }
            }

            // Input manuale — alternativa al codice a barre per prodotti sfusi o non trovati
            HorizontalDivider()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        "Nessun codice a barre? Scrivi l'ingrediente manualmente:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val keyboardController = LocalSoftwareKeyboardController.current
                        OutlinedTextField(
                            value = manualInput,
                            onValueChange = { manualInput = it },
                            placeholder = { Text("Es: mela, tonno, pasta…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val trimmed = manualInput.trim().lowercase()
                                if (trimmed.isNotEmpty()) {
                                    viewModel.addIngredient(trimmed)
                                    flashMessage = "✅ Aggiunto: $trimmed"
                                    manualInput = ""
                                    keyboardController?.hide()
                                }
                            })
                        )
                        Button(
                            onClick = {
                                val trimmed = manualInput.trim().lowercase()
                                if (trimmed.isNotEmpty()) {
                                    viewModel.addIngredient(trimmed)
                                    flashMessage = "✅ Aggiunto: $trimmed"
                                    manualInput = ""
                                }
                            },
                            enabled = manualInput.trim().isNotEmpty()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Aggiungi")
                        }
                    }
                }
            }
        }
    }
}

private fun setupCamera(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    cameraExecutor: Executor,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onBarcodeDetected: (String) -> Unit,
    onCameraReady: (androidx.camera.core.Camera, ProcessCameraProvider) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageAnalysis.setAnalyzer(cameraExecutor, BarcodeAnalyzer(barcodeScanner, onBarcodeDetected))

        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
            onCameraReady(camera, cameraProvider)
        } catch (e: Exception) {
            Log.e("FrigoZero", "Camera binding failed", e)
        }
    }, mainThreadExecutor())
}

private class BarcodeAnalyzer(
    private val scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    private val onBarcodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onBarcodeDetected)
            }
            .addOnFailureListener { e ->
                Log.w("FrigoZero", "Barcode scan failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

private fun findLifecycleOwner(context: Context): androidx.lifecycle.LifecycleOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is androidx.lifecycle.LifecycleOwner) {
            return current
        }
        current = current.baseContext
    }
    return null
}

private fun mainThreadExecutor(): Executor {
    val handler = Handler(Looper.getMainLooper())
    return Executor { runnable -> handler.post(runnable) }
}

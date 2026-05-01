package com.example.frigozero.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.frigozero.data.IngredientCatalog
import com.example.frigozero.viewmodel.FrigoViewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private data class ScanLabel(
    val text: String,
    val confidence: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: FrigoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = remember(context) { findLifecycleOwner(context) }

    if (lifecycleOwner == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Errore: lifecycle non disponibile per la fotocamera")
        }
        return
    }

    var detectedLabels by remember { mutableStateOf<List<String>>(emptyList()) }
    var flashMessage by remember { mutableStateOf("") }
    var captureUseCase by remember { mutableStateOf<ImageCapture?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scansiona Ingrediente") },
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
                        setupCamera(ctx, lifecycleOwner, previewView) { imageCapture ->
                            captureUseCase = imageCapture
                        }
                        previewView
                    }
                )

                // Scan frame overlay
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                )

                // Flash message overlay
                if (flashMessage.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(flashMessage, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Detected labels
            if (detectedLabels.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Rilevato:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        detectedLabels.take(5).forEach { label ->
                            Text(
                                "• $label",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Capture button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Inquadra l'ingrediente e premi il pulsante",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FloatingActionButton(
                        onClick = {
                            captureUseCase?.let { capture ->
                                analyzeImage(
                                    context = context,
                                    imageCapture = capture,
                                    executor = cameraExecutor,
                                    onResult = { labels ->
                                        val labelTexts = labels.map { it.text }
                                        val specificIngredients =
                                            IngredientCatalog.extractSpecificIngredients(labelTexts)
                                        val scanIngredients = if (specificIngredients.isNotEmpty()) {
                                            specificIngredients
                                        } else {
                                            IngredientCatalog.extractBestEffortIngredients(labelTexts)
                                        }

                                        detectedLabels = if (scanIngredients.isNotEmpty()) {
                                            scanIngredients
                                        } else {
                                            labels.map { "${it.text} (${(it.confidence * 100).toInt()}%)" }
                                        }

                                        if (scanIngredients.isNotEmpty()) {
                                            scanIngredients.forEach { viewModel.addIngredient(it) }
                                            flashMessage =
                                                "✅ Aggiunti: ${scanIngredients.joinToString(", ")}"
                                        } else {
                                            flashMessage =
                                                "Nessun ingrediente riconosciuto. Prova luce migliore e inquadra l'etichetta frontalmente."
                                        }
                                    }
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.Default.Camera,
                            contentDescription = "Scatta foto",
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
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
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
            onImageCaptureReady(imageCapture)
        } catch (e: Exception) {
            Log.e("FrigoZero", "Camera binding failed", e)
        }
    }, mainThreadExecutor())
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

private fun analyzeImage(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onResult: (List<ScanLabel>) -> Unit
) {
    val photoFile = File.createTempFile("scan_", ".jpg", context.cacheDir)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                if (bitmap == null) {
                    Log.e("FrigoZero", "Failed to decode captured image")
                    photoFile.delete()
                    onResult(emptyList())
                    return
                }

                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val labeler = ImageLabeling.getClient(
                    ImageLabelerOptions.Builder()
                        .setConfidenceThreshold(0.35f)
                        .build()
                )
                labeler.process(inputImage)
                    .addOnSuccessListener { labels ->
                        val results = labels
                            .sortedByDescending(ImageLabel::getConfidence)
                            .map { ScanLabel(it.text, it.confidence) }
                        onResult(results)
                        photoFile.delete()
                    }
                    .addOnFailureListener { e ->
                        Log.e("FrigoZero", "Labeling failed", e)
                        photoFile.delete()
                        onResult(emptyList())
                    }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("FrigoZero", "Capture failed", exception)
            }
        }
    )
}


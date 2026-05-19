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

private data class ScanCandidate(
    val ingredient: String,
    val confidence: Float?,
    val fromCanonicalMatch: Boolean
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
    var scanCandidates by remember { mutableStateOf<List<ScanCandidate>>(emptyList()) }
    var selectedCandidates by remember { mutableStateOf<Set<String>>(emptySet()) }
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

            if (scanCandidates.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "Ingredienti riconosciuti",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Se ci sono piu opzioni, seleziona quelle attendibili e conferma.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            scanCandidates.forEach { candidate ->
                                val selected = selectedCandidates.contains(candidate.ingredient)
                                val confidenceLabel = candidate.confidence?.let {
                                    " ${(it * 100).toInt()}%"
                                }.orEmpty()
                                val chipLabel = buildString {
                                    append(candidate.ingredient.replaceFirstChar { it.uppercase() })
                                    append(confidenceLabel)
                                    if (candidate.fromCanonicalMatch) {
                                        append(" ✓")
                                    }
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        selectedCandidates = if (selected) {
                                            selectedCandidates - candidate.ingredient
                                        } else {
                                            selectedCandidates + candidate.ingredient
                                        }
                                    },
                                    label = { Text(chipLabel) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                selectedCandidates.forEach { viewModel.addIngredient(it) }
                                flashMessage = "✅ Aggiunti: ${selectedCandidates.joinToString(", ")}"
                                detectedLabels = selectedCandidates.toList()
                            },
                            enabled = selectedCandidates.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Aggiungi selezionati")
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
                                        val candidates = buildScanCandidates(labels)
                                        scanCandidates = candidates
                                        selectedCandidates = candidates
                                            .filter { it.fromCanonicalMatch && (it.confidence ?: 0f) >= 0.45f }
                                            .map { it.ingredient }
                                            .toSet()

                                        detectedLabels = candidates.map { it.ingredient }

                                        flashMessage = if (candidates.isNotEmpty()) {
                                            "✅ Trovati ${candidates.size} ingredienti — seleziona e conferma."
                                        } else {
                                            "Nessun ingrediente riconosciuto. Centra bene l'ingrediente su sfondo pulito e riprova."
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

private fun buildScanCandidates(labels: List<ScanLabel>): List<ScanCandidate> {
    if (labels.isEmpty()) {
        return emptyList()
    }

    val filteredLabels = labels.filter { label ->
        label.confidence >= 0.25f && !IngredientCatalog.isLikelyNonIngredientLabel(label.text)
    }

    val canonicalCandidates = linkedMapOf<String, Float>()
    filteredLabels.forEach { label ->
        val canonical = IngredientCatalog.toCanonicalIngredient(label.text) ?: return@forEach
        val previous = canonicalCandidates[canonical]
        if (previous == null || label.confidence > previous) {
            canonicalCandidates[canonical] = label.confidence
        }
    }

    val rankedCanonical = canonicalCandidates
        .entries
        .sortedByDescending { it.value }
        .map { entry ->
            ScanCandidate(
                ingredient = entry.key,
                confidence = entry.value,
                fromCanonicalMatch = true
            )
        }

    // Strict mode: only canonical ingredients to minimize false positives.
    return rankedCanonical
        .distinctBy { it.ingredient }
        .take(8)
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
                        .setConfidenceThreshold(0.40f)
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


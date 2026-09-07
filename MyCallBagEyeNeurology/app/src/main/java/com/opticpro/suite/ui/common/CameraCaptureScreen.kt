package com.opticpro.suite.ui.common

import android.content.ContentValues
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Schermata generica: anteprima fotocamera + scatto foto salvata in galleria (MediaStore) + torcia.
 * Riutilizzata per Funduscopia, Fotografia lampada a fessura, Strabismo selfie, ecc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCaptureScreen(
    title: String,
    instructions: String,
    onBack: () -> Unit,
    facing: Int = CameraSelector.LENS_FACING_BACK,
    albumName: String = "OpticProSuite"
) {
    val context = LocalContext.current
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var lastSavedMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(instructions, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CameraCapturePreview(modifier = Modifier.fillMaxSize(), facing = facing) { cam, capture ->
                    camera = cam
                    imageCapture = capture
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                }) { Icon(Icons.Filled.FlashOn, contentDescription = "Torcia") }

                Button(onClick = {
                    val capture = imageCapture ?: return@Button
                    val name = "OPS_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALY).format(Date())}.jpg"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$albumName")
                    }
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(
                        context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                    ).build()
                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                lastSavedMessage = "Foto salvata: $name"
                            }
                            override fun onError(exception: ImageCaptureException) {
                                lastSavedMessage = "Errore scatto: ${exception.message}"
                            }
                        }
                    )
                }) {
                    Icon(Icons.Filled.Camera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scatta")
                }
            }
            lastSavedMessage?.let {
                Text(it, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** CameraPreview con accesso a Camera e ImageCapture per scatto/torcia. */
@Composable
fun CameraCapturePreview(
    modifier: Modifier = Modifier,
    facing: Int = CameraSelector.LENS_FACING_BACK,
    onReady: (androidx.camera.core.Camera, ImageCapture) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(android.Manifest.permission.CAMERA)
    }

    Box(modifier = modifier) {
        if (hasPermission) {
            AndroidView(factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageCapture = ImageCapture.Builder().build()
                    val selector = CameraSelector.Builder().requireLensFacing(facing).build()
                    runCatching {
                        cameraProvider.unbindAll()
                        val cam = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                        onReady(cam, imageCapture)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }, modifier = Modifier.fillMaxSize())
        }
    }
}

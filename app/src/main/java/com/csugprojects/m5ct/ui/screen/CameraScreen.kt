package com.csugprojects.m5ct.ui.screen

import android.content.Context
import android.net.Uri
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.csugprojects.m5ct.ui.viewmodel.GalleryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

// Utility function to get the required executor for CameraX operations.
@Composable
private fun rememberCameraExecutor(): Executor {
    val context = LocalContext.current
    return remember {
        ContextCompat.getMainExecutor(context)
    }
}

/**
 * The CameraScreen Composable (View Layer in MVVM).
 * This implements the CameraX integration requirement for the assignment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(navController: NavHostController, viewModel: GalleryViewModel) {
    val context = LocalContext.current
    // Gets the current lifecycle owner for binding the camera.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraExecutor = rememberCameraExecutor()
    val coroutineScope = rememberCoroutineScope()

    // Configures the ImageCapture use case for the camera.
    @Suppress("DEPRECATION")
    val imageCapture = remember {
        ImageCapture.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val isCapturing = remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capture Photo (CameraX)") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Embeds the CameraX PreviewView into the Jetpack Compose layout.
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        bindCameraUseCases(cameraProvider, lifecycleOwner, previewView, imageCapture)
                    }, cameraExecutor)

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Button to trigger the photo capture.
            FloatingActionButton(
                onClick = {
                    if (!isCapturing.value) {
                        isCapturing.value = true

                        // Launches the capture operation on a background thread.
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val savedUri = takePhotoAndSave(context, cameraExecutor, imageCapture, viewModel, coroutineScope)

                                // Switches back to the main thread for UI changes (navigation).
                                withContext(Dispatchers.Main) {
                                    if (savedUri != null) {
                                        viewModel.handleNewCapturedPhoto(savedUri)
                                        navController.popBackStack()
                                    }
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isCapturing.value = false
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                // Shows a loading spinner while processing the photo.
                if (isCapturing.value) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "Take Photo")
                }
            }
        }
    }
}

/**
 * Binds the camera preview and capture use cases to the Android lifecycle owner.
 */
private fun bindCameraUseCases(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    imageCapture: ImageCapture
) {
    cameraProvider.unbindAll()

    val preview = Preview.Builder().build().also {
        it.surfaceProvider = previewView.surfaceProvider
    }

    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    try {
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture
        )
    } catch (exc: Exception) {
        println("Binding failed: $exc")
    }
}


/**
 * Suspends the coroutine until CameraX saves the picture, then delegates saving the file.
 * The inner saving process is delegated to the Repository (Model Layer).
 */
private suspend fun takePhotoAndSave(
    context: Context,
    cameraExecutor: Executor,
    imageCapture: ImageCapture,
    viewModel: GalleryViewModel,
    parentScope: CoroutineScope
): Uri? {
    // Creates a temporary file to hold the raw image data immediately after capture.
    val photoFile = File(
        context.cacheDir,
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    // Uses suspendCancellableCoroutine to bridge the callback-based CameraX API with Kotlin coroutines.
    return suspendCancellableCoroutine { continuation ->
        imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {

            override fun onError(exc: ImageCaptureException) {
                println("Photo capture failed: ${exc.message}")
                continuation.resume(null)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val displayName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

                // Launches the file persistence operation.
                parentScope.launch {
                    try {
                        // The Repository handles the complex file I/O and MediaStore updates.
                        val savedUri = viewModel.repository.saveCapturedPhoto(photoFile, displayName)

                        continuation.resume(savedUri)
                    } catch (e: Exception) {
                        println("Error saving image via Repository: ${e.message}")
                        photoFile.delete()
                        continuation.resume(null)
                    }
                }
            }
        })
    }
}
package com.csugprojects.m5ct.ui.screen

import android.content.Context
import android.net.Uri
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

// --- 1. Camera Utility Functions ---

@Composable
private fun rememberCameraExecutor(): Executor {
    val context = LocalContext.current
    return remember {
        ContextCompat.getMainExecutor(context)
    }
}

// --- 2. Camera Screen Composable (M5 Multimedia - View Layer) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(navController: NavHostController, viewModel: GalleryViewModel) {
    val context = LocalContext.current
    // FIX: Uses the correct lifecycle owner from the lifecycle-runtime-compose library
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraExecutor = rememberCameraExecutor()
    val coroutineScope = rememberCoroutineScope()

    val imageCapture = remember { ImageCapture.Builder().build() }
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
            // M5: AndroidView Integration for CameraX PreviewView
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

            FloatingActionButton(
                onClick = {
                    if (!isCapturing.value) {
                        isCapturing.value = true

                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val savedUri = takePhotoAndSave(context, cameraExecutor, imageCapture, viewModel)

                                // FIX APPLIED: Switch to the Main thread before updating the UI and navigating.
                                withContext(Dispatchers.Main) {
                                    if (savedUri != null) {
                                        // These lines safely execute on the Main Thread now
                                        viewModel.handleNewCapturedPhoto(savedUri)
                                        navController.popBackStack() // CRASH AVOIDED
                                    }
                                }
                            } finally {
                                // Ensure loading state is reset, also preferably on Main
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

// --- 3. Core CameraX Binding and Capture I/O Logic (Non-Composable) ---

/**
 * Binds CameraX Preview and ImageCapture use cases to the composable lifecycle.
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
 * Executes photo capture and handles file persistence via the Repository.
 */
private suspend fun takePhotoAndSave(
    context: Context,
    cameraExecutor: Executor,
    imageCapture: ImageCapture,
    viewModel: GalleryViewModel
): Uri? {
    val photoFile = File(
        context.cacheDir,
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis()) + ".jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    return suspendCancellableCoroutine { continuation ->
        imageCapture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {

            override fun onError(exc: ImageCaptureException) {
                println("Photo capture failed: ${exc.message}")
                continuation.resume(null)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val displayName = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

                // CRITICAL FIX: Launch a new coroutine on the I/O thread to safely execute the suspend function.
                // This resolves the Main Thread crash when calling the suspending repository function.
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // 1. Delegate persistence (M3/M5 I/O)
                        val savedUri = viewModel.repository.saveCapturedPhoto(photoFile, displayName)

                        // 2. Resume the outer coroutine with the final result
                        continuation.resume(savedUri)
                    } catch (e: Exception) {
                        println("Error saving image via Repository: ${e.message}")
                        // Ensure temporary file is deleted if save failed
                        photoFile.delete()
                        continuation.resume(null)
                    }
                }
            }
        })
    }
}
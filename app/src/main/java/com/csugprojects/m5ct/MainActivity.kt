package com.csugprojects.m5ct

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.csugprojects.m5ct.di.GalleryViewModelFactory
import com.csugprojects.m5ct.data.repository.ImageRepository
import com.csugprojects.m5ct.data.local.MediaStoreDataSource
import com.csugprojects.m5ct.data.remote.UnsplashApiService
import com.csugprojects.m5ct.data.remote.UnsplashApi
import com.csugprojects.m5ct.ui.viewmodel.GalleryViewModel
import com.csugprojects.m5ct.navigation.AppNavigation
import com.csugprojects.m5ct.ui.theme.M5CTTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

// =========================================================================
// 1. DEPENDENCY SETUP & PERMISSIONS
// =========================================================================

// M5: Defines the list of mandatory permissions for CameraX
private val REQUIRED_PERMISSIONS = listOfNotNull(
    Manifest.permission.CAMERA // FIX: ONLY Camera permission is requested at launch.
)

class MainActivity : ComponentActivity() {

    private lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MANUAL DEPENDENCY INJECTION (DI) SETUP: Creates the entire data graph
        // This process satisfies the Model (M3/M4) layer requirements.
        val mediaStoreDataSource = MediaStoreDataSource(applicationContext)
        val unsplashApiImpl: UnsplashApi = UnsplashApiService.api // Concrete API service
        val imageRepository = ImageRepository(unsplashApiImpl, mediaStoreDataSource)

        // Final object needed by the UI
        viewModelFactory = GalleryViewModelFactory(imageRepository)

        setContent {
            // M5: Apply the Material 3 Theme (View Layer)
            M5CTTheme {
                // Launch the root composable, gating navigation behind permissions
                PermissionHandler(viewModelFactory = viewModelFactory)
            }
        }
    }
}

// =========================================================================
// 2. PERMISSION HANDLER (Root View/Controller)
// =========================================================================

/**
 * Handles runtime permission requests and conditionally launches the main app navigation.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PermissionHandler(viewModelFactory: ViewModelProvider.Factory) {
    // M5: Use Accompanist to manage and observe the permission state
    val permissionState = rememberMultiplePermissionsState(
        permissions = REQUIRED_PERMISSIONS
    )

    // Instantiate ViewModel using the provided Factory (MVVM Injection)
    val viewModel: GalleryViewModel = viewModel(factory = viewModelFactory)

    val allGranted = permissionState.allPermissionsGranted

    // LAUNCHED EFFECT REMOVED: Redundant as navigation is handled by the if statement below.

    if (allGranted) {
        // If granted (only Camera needed now), proceed to the main navigation graph
        AppNavigation(viewModel)
    } else {
        // Graceful fallback/explanation UI using M3 components
        Scaffold(
            topBar = { TopAppBar(title = { Text("Permissions Required") }) },
            modifier = Modifier.fillMaxSize()
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "This gallery requires Camera access to take photos. Grant access to continue. Local images are accessed via the Photo Picker.",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = { permissionState.launchMultiplePermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Grant Camera Permission", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

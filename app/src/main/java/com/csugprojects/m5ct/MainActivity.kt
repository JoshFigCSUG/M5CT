package com.csugprojects.m5ct

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.csugprojects.m5ct.di.GalleryViewModelFactory
import com.csugprojects.m5ct.data.repository.ImageRepository
import com.csugprojects.m5ct.data.local.MediaStoreDataSource
import com.csugprojects.m5ct.data.remote.UnsplashApiService
import com.csugprojects.m5ct.data.remote.UnsplashApi
// REMOVED: Incomplete Permission logic imports (Manifest, ExperimentalPermissionsApi, etc.)
import com.csugprojects.m5ct.ui.theme.M5CTTheme
// NEW: Import the correct PermissionHandler from the component package
import com.csugprojects.m5ct.ui.component.PermissionHandler

// =========================================================================
// 1. DEPENDENCY SETUP
// =========================================================================

// REMOVED: private val REQUIRED_PERMISSIONS = listOfNotNull(Manifest.permission.CAMERA)

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

// REMOVED: The entire PermissionHandler @Composable function below was removed.
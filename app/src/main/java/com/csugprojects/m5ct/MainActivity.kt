// app/src/main/java/com/csugprojects/m5ct/MainActivity.kt

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
import com.csugprojects.m5ct.ui.theme.M5CTTheme
// REMOVED: import com.csugprojects.m5ct.ui.component.PermissionHandler
import com.csugprojects.m5ct.navigation.AppNavigation // NEW: Import AppNavigation directly

// =========================================================================
// 1. DEPENDENCY SETUP
// =========================================================================

class MainActivity : ComponentActivity() {

    private lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MANUAL DEPENDENCY INJECTION (DI) SETUP: Creates the entire data graph
        val mediaStoreDataSource = MediaStoreDataSource(applicationContext)
        val unsplashApiImpl: UnsplashApi = UnsplashApiService.api
        val imageRepository = ImageRepository(unsplashApiImpl, mediaStoreDataSource)

        // Final object needed by the UI
        viewModelFactory = GalleryViewModelFactory(imageRepository)

        setContent {
            // M5: Apply the Material 3 Theme (View Layer)
            val viewModel = viewModelFactory.create(com.csugprojects.m5ct.ui.viewmodel.GalleryViewModel::class.java) // Manually create ViewModel

            M5CTTheme {
                // Launch AppNavigation directly, without permission gating.
                // Permissions will now be checked within individual screens (GalleryScreen/CameraScreen).
                AppNavigation(viewModel)
            }
        }
    }
}
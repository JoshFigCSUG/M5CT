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
import com.csugprojects.m5ct.navigation.AppNavigation
import com.csugprojects.m5ct.ui.viewmodel.GalleryViewModel

/**
 * The main activity of the application.
 * This class is responsible for setting up the environment, including Dependency Injection (DI)
 * and initializing the Jetpack Compose UI.
 */
class MainActivity : ComponentActivity() {

    // Holds the factory needed to create the ViewModel with its dependencies.
    private lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Manual Dependency Injection Setup (Composition Root).
        // 1. Create Data Sources (Local and Remote).
        val mediaStoreDataSource = MediaStoreDataSource(applicationContext)
        val unsplashApiImpl: UnsplashApi = UnsplashApiService.api

        // 2. Create the Repository, injecting the two data sources.
        val imageRepository = ImageRepository(unsplashApiImpl, mediaStoreDataSource)

        // 3. Create the Factory, injecting the Repository.
        viewModelFactory = GalleryViewModelFactory(imageRepository)

        setContent {
            // Applies the customized Material 3 Theme to the entire application.
            M5CTTheme {
                // Manually obtains the ViewModel using the custom factory.
                val viewModel = viewModelFactory.create(GalleryViewModel::class.java)

                // Starts the Compose navigation graph.
                AppNavigation(viewModel)
            }
        }
    }
}
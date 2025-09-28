package com.csugprojects.m5ct.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.csugprojects.m5ct.ui.viewmodel.GalleryViewModel
import com.csugprojects.m5ct.ui.screen.GalleryScreen
import com.csugprojects.m5ct.ui.screen.CameraScreen
import com.csugprojects.m5ct.ui.screen.DetailScreen

/**
 * Sets up the central navigation graph using NavHost for the application.
 * This file maps the defined Routes to the corresponding Composable screens (View Layer).
 */
@Composable
fun AppNavigation(viewModel: GalleryViewModel = viewModel()) {
    // Manages the back stack and navigation state for the application.
    val navController = rememberNavController()

    // Defines the navigation routes and the starting screen for the application.
    NavHost(
        navController = navController,
        startDestination = Routes.GALLERY // The initial screen loaded when the app starts.
    ) {

        // Route for the main photo grid screen.
        composable(Routes.GALLERY) {
            // Passes the navigation controller and the shared ViewModel to the screen.
            GalleryScreen(navController, viewModel)
        }

        // Route for the CameraX capture screen.
        composable(Routes.CAMERA) {
            CameraScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        // Route for the single photo detail view.
        composable(Routes.DETAIL) {
            DetailScreen(navController, viewModel)
        }
    }
}
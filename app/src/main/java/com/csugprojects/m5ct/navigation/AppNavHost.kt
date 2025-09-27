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
 * Sets up the central navigation graph (NavHost) for the application.
 * This is where routes (from Routes.kt) are mapped to Composable screens (View layer).
 */
@Composable
fun AppNavigation(viewModel: GalleryViewModel = viewModel()) {
    // Instantiates the NavController, which manages the back stack and navigation state
    val navController = rememberNavController()

    // Sets up the NavHost, starting at the main gallery screen
    NavHost(
        navController = navController,
        startDestination = Routes.GALLERY
    ) {

        // Route for the Main Gallery Grid (Home Screen)
        composable(Routes.GALLERY) {
            // Passes the navController and the ViewModel to the screen
            GalleryScreen(navController, viewModel)
        }

        // Route for the Camera Capture Screen (M5 Multimedia)
        composable(Routes.CAMERA) {
            CameraScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        // Route for the Single Photo Detail View
        composable(Routes.DETAIL) {
            DetailScreen(navController, viewModel)
        }

        // Note: Permission handling is managed outside this NavHost in MainActivity.kt
    }
}
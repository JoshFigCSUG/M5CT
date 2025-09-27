package com.csugprojects.m5ct.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.csugprojects.m5ct.ui.viewmodel.GalleryViewModel

/**
 * Screen displaying the single selected image in full detail. (View Layer)
 * It observes the selectedImage state from the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(navController: NavHostController, viewModel: GalleryViewModel) {

    // OBSERVE STATE: Collects the nullable GalleryItem from the ViewModel's StateFlow (M3/M4 data).
    // initial = null is used to satisfy the Flow extension function requirement.
    val selectedImage by viewModel.selectedImage.collectAsState(initial = null)

    // M3: Use Scaffold for the screen structure
    Scaffold(
        topBar = {
            TopAppBar(
                // Use safe call (?.), relying on the Elvis operator (?:) for a default title.
                title = { Text(selectedImage?.description ?: "Image Detail") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Gallery")
                    }
                },
                actions = {
                    // FIX APPLIED: Only check if the photo is locally marked.
                    // This restores the delete button for CameraX-captured photos (where isLocal is true).
                    if (selectedImage?.isLocal == true) {
                        IconButton(onClick = {
                            viewModel.deleteSelectedPhoto()
                            navController.popBackStack()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Photo")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            // Use .let to safely access properties only if selectedImage is not null.
            selectedImage?.let { item ->
                // M5: Coil integration for image loading
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.fullUrl) // Use full resolution URL
                        .crossfade(true)
                        .build(),
                    contentDescription = item.description,
                    contentScale = ContentScale.Fit, // Display the entire image
                    modifier = Modifier.fillMaxSize()
                )
            } ?: Text("Image not found. Please select an image from the gallery.")
        }
    }
}
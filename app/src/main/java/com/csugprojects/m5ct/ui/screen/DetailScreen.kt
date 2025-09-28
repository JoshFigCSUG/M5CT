package com.csugprojects.m5ct.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.csugprojects.m5ct.ui.viewmodel.GalleryViewModel
import com.csugprojects.m5ct.data.model.GalleryItem
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collectLatest

// This Composable is part of the View Layer in the MVVM architecture.
// It displays a single image and its detailed metadata.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(navController: NavHostController, viewModel: GalleryViewModel) {

    // Observes the currently selected image from the ViewModel's state.
    val selectedImage by viewModel.selectedImage.collectAsState(initial = null)
    val scrollState = rememberScrollState()

    // Uses LaunchedEffect to observe the ViewModel for a successful photo deletion event.
    // This handles navigation as a side effect outside of the button's onClick handler for safety.
    LaunchedEffect(key1 = true) {
        viewModel.deleteSuccess.collectLatest { success ->
            if (success) {
                // Navigates back to the gallery upon successful deletion.
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedImage?.description ?: "Image Detail") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Gallery")
                    }
                },
                actions = {
                    // Displays the delete button only if the photo is locally owned by the app.
                    if (selectedImage?.isLocal == true) {
                        IconButton(onClick = {
                            // Triggers the deletion logic in the ViewModel (delegates the Model task).
                            viewModel.deleteSelectedPhoto()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Photo")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Makes the content scrollable for adaptive UI design.
                .verticalScroll(scrollState)
        ) {
            selectedImage?.let { item ->
                // Displays the high-resolution image using the Coil library.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.fullUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.description,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Displays the metadata details card.
                DetailsCard(item = item)
            } ?: Text(
                "Image not found. Please select an image from the gallery.",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

// Custom Composable to display the structured photo metadata.
@Composable
fun DetailsCard(item: GalleryItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // General Information Section
            Text(
                "General Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            DetailsRow(label = "Photo ID", value = item.id)
            DetailsRow(label = "Description", value = item.description ?: "N/A")

            val sourceValue = if (item.isLocal) "Local Device / CameraX" else item.photographer ?: "Unsplash Contributor"
            DetailsRow(label = if (item.isLocal) "Source" else "Photographer", value = sourceValue)

            DetailsRow(label = "Pixel Size", value = item.pixelSize ?: "N/A")
            DetailsRow(label = "File Size", value = item.fileSize ?: "N/A")
            DetailsRow(label = "Filename", value = item.filename ?: "N/A")

            // Location Details Section
            if (item.locationName != null || item.locationCity != null || item.locationCountry != null) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    "Location Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                DetailsRow(label = "Full Location Name", value = item.locationName ?: "N/A")
                DetailsRow(label = "City", value = item.locationCity ?: "N/A")
                DetailsRow(label = "Country", value = item.locationCountry ?: "N/A")
            }

            // Camera (EXIF) Details Section
            if (item.cameraModel != null || item.cameraMake != null || item.exposureTime != null) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(
                    "Camera (EXIF) Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                DetailsRow(label = "Make", value = item.cameraMake ?: "N/A")
                DetailsRow(label = "Model", value = item.cameraModel ?: "N/A")
                DetailsRow(label = "Exposure Time", value = item.exposureTime ?: "N/A")
                DetailsRow(label = "Aperture Value", value = item.aperture ?: "N/A")
                DetailsRow(label = "Focal Length", value = item.focalLength ?: "N/A")
                DetailsRow(label = "ISO Speed", value = item.iso ?: "N/A")
            }
        }
    }
}

// Reusable Composable for displaying a single label/value pair of metadata.
@Composable
fun DetailsRow(label: String, value: String) {
    if (value.isNotBlank() && value != "N/A") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
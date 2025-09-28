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
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect // NEW IMPORT
import kotlinx.coroutines.flow.collectLatest // NEW IMPORT

/**
 * Screen displaying the single selected image in full detail, including comprehensive metadata. (View Layer)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(navController: NavHostController, viewModel: GalleryViewModel) {

    // Observes the currently selected image from the ViewModel
    val selectedImage by viewModel.selectedImage.collectAsState(initial = null)
    val scrollState = rememberScrollState()
    // val coroutineScope = rememberCoroutineScope() // REMOVED

    // NEW ROBUSTNESS: Use LaunchedEffect to observe the one-time deletion event
    LaunchedEffect(key1 = true) {
        viewModel.deleteSuccess.collectLatest { success ->
            if (success) {
                // Perform the navigation side-effect after the ViewModel signals completion
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
                    // Show delete button only if the image is local (app-owned)
                    if (selectedImage?.isLocal == true) {
                        IconButton(onClick = {
                            // MODIFIED: Call the non-suspend function, which manages the coroutine internally
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
                .verticalScroll(scrollState) // Makes the content scrollable
        ) {
            selectedImage?.let { item ->
                // 1. Full Image Display Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp) // Constrain height on smaller screens
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

                // 2. Metadata Card (The Detail Information)
                DetailsCard(item = item)
            } ?: Text(
                "Image not found. Please select an image from the gallery.",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/**
 * Custom Composable to display the structured photo metadata using a Material Card.
 */
@Composable
fun DetailsCard(item: GalleryItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // --- 1. GENERAL INFORMATION ---
            Text(
                "General Information",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            DetailsRow(label = "Photo ID", value = item.id)
            DetailsRow(label = "Description", value = item.description ?: "N/A")

            // Photographer or Source
            val sourceValue = if (item.isLocal) "Local Device / CameraX" else item.photographer ?: "Unsplash Contributor"
            DetailsRow(label = if (item.isLocal) "Source" else "Photographer", value = sourceValue)

            DetailsRow(label = "Pixel Size", value = item.pixelSize ?: "N/A")
            DetailsRow(label = "File Size", value = item.fileSize ?: "N/A")
            DetailsRow(label = "Filename", value = item.filename ?: "N/A")

            // --- 2. LOCATION ---
            // Check if any location data exists before drawing the section
            if (item.locationName != null || item.locationCity != null || item.locationCountry != null) {
                Divider(Modifier.padding(vertical = 12.dp))
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

            // --- 3. EXIF DATA (Camera Details) ---
            // Check if any EXIF data exists before drawing the section
            if (item.cameraModel != null || item.cameraMake != null || item.exposureTime != null) {
                Divider(Modifier.padding(vertical = 12.dp))
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

/**
 * Reusable Row for displaying a single label/value pair.
 * It intelligently hides the row if the value is "N/A".
 */
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
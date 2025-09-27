package com.csugprojects.m5ct.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.csugprojects.m5ct.navigation.Routes
import com.csugprojects.m5ct.data.model.GalleryItem
import com.csugprojects.m5ct.ui.viewmodel.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The main screen of the application (View Layer).
 * It uses the Android Photo Picker (policy compliant) for local image selection and processing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(navController: NavHostController, viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val images by viewModel.images.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    // --- PHOTO PICKER LAUNCHER (M5 Policy Compliance) ---
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                coroutineScope.launch(Dispatchers.IO) { // 1. Start I/O on background thread

                    // A. Get the list of new, persisted items from the ViewModel logic
                    val newItems = viewModel.processAndCopyPickerUris(context, uris)

                    // B. CRITICAL FIX: Switch back to the Main Thread for safe state modification
                    withContext(Dispatchers.Main) {
                        // The state update MUST happen here, guaranteeing Main Thread access
                        viewModel.handleNewPhotoPickerUris(newItems)
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modern Photo Gallery") },
                actions = {
                    // 1. Button to launch the system Photo Picker (Manual User Action)
                    IconButton(onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) {
                        Icon(Icons.Filled.Image, contentDescription = "Select Local Images")
                    }

                    // 2. Button to launch the CameraX screen
                    IconButton(onClick = { navController.navigate(Routes.CAMERA) }) {
                        Icon(Icons.Filled.Camera, contentDescription = "Open Camera (M5)")
                    }
                }
            )
        }
    ) { padding ->
        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Tap the image icon to select local photos or wait for remote load.")
            }
        } else {
            // M5: Use LazyVerticalGrid for efficient display
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(images, key = { it.id }) { item ->
                    GalleryGridItem(item) {
                        viewModel.selectImage(item)
                        navController.navigate(Routes.DETAIL)
                    }
                }
            }
        }
    }
}

/**
 * Reusable Composable for a single image tile in the grid.
 * Encapsulates Coil integration logic.
 */
@Composable
fun GalleryGridItem(item: GalleryItem, onClick: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        // M5: Coil integration for efficient loading
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.regularUrl)
                .crossfade(true)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = item.description,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
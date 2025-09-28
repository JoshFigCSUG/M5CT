package com.csugprojects.m5ct.ui.screen // <--- CRITICAL: MUST be this package

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
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
import com.csugprojects.m5ct.ui.component.STORAGE_PERMISSIONS
import com.csugprojects.m5ct.ui.component.CAMERA_PERMISSIONS
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

/**
 * The main screen of the application (View Layer), featuring the DockedSearchBar and image grid.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun GalleryScreen(navController: NavHostController, viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val images by viewModel.images.collectAsState(initial = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // State to manage the search bar's active/expanded state
    var isSearchActive by remember { mutableStateOf(false) }

    // --- PHOTO PICKER LAUNCHER (Policy Compliant Import) ---
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                coroutineScope.launch(Dispatchers.IO) {
                    val newItems = viewModel.processAndCopyPickerUris(context, uris)
                    withContext(Dispatchers.Main) {
                        viewModel.handleNewPhotoPickerUris(newItems)
                    }
                }
            }
        }
    )

    // Permission state for local storage access (only for reading/writing local files)
    val storagePermissionState = rememberMultiplePermissionsState(
        permissions = STORAGE_PERMISSIONS
    )

    // Permission state for Camera access
    val cameraPermissionState = rememberMultiplePermissionsState(
        permissions = CAMERA_PERMISSIONS
    )

    // REVERTED: Removed the LaunchedEffect that automatically launched the picker after permissions were granted.
    // The user must now click the image button a second time after granting permission.


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modern Photo Gallery") },
                actions = {
                    // 1. Button to launch the system Photo Picker (STORAGE PERMISSION CHECK)
                    if (storagePermissionState.allPermissionsGranted) {
                        // Permissions granted: Clicks launch the picker directly.
                        IconButton(onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }) {
                            Icon(Icons.Filled.Image, contentDescription = "Select Local Images")
                        }
                    } else {
                        // Permissions not granted: Button requests permissions.
                        IconButton(onClick = {
                            storagePermissionState.launchMultiplePermissionRequest()
                        }) {
                            Icon(Icons.Filled.Image, contentDescription = "Request Storage Permissions")
                        }
                    }

                    // 2. Button to launch the CameraX screen (CAMERA PERMISSION CHECK)
                    if (cameraPermissionState.allPermissionsGranted) {
                        IconButton(onClick = { navController.navigate(Routes.CAMERA) }) {
                            Icon(Icons.Filled.Camera, contentDescription = "Open Camera (M5)")
                        }
                    } else {
                        IconButton(onClick = { cameraPermissionState.launchMultiplePermissionRequest() }) {
                            Icon(Icons.Filled.Camera, contentDescription = "Request Camera Permissions")
                        }
                    }
                }
            )
        }
    ) { padding ->
        // Use a Column to hold the search bar and the main content list
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // DockedSearchBar implementation
            DockedSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onSearch = {
                    isSearchActive = false
                },
                placeholder = { Text("Search Unsplash") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear Search")
                        }
                    }
                },
                active = isSearchActive,
                onActiveChange = { active -> isSearchActive = active },

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                // Content of the expanded search (suggestions/history) can go here
            }

            // Main Content: Lazy Grid
            if (images.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val message = when {
                        // 1. Show Network/API Error if present
                        !errorMessage.isNullOrEmpty() -> errorMessage!!
                        // 2. Show No Search Results (query is not blank, but images are empty)
                        searchQuery.isNotBlank() -> "No images found for \"$searchQuery\"."
                        // 3. Initial/Empty State (If local images can't load without permission)
                        !storagePermissionState.allPermissionsGranted -> "Local images require storage permission. Loading network images..."
                        else -> "Loading random images or check connectivity."
                    }
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
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
}

/**
 * Reusable Composable for a single image tile in the grid, encapsulating Coil loading.
 */
@Composable
fun GalleryGridItem(item: GalleryItem, onClick: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        // Coil integration for efficient loading
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
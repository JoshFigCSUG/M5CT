package com.csugprojects.m5ct.ui.component

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.csugprojects.m5ct.ui.viewmodel.GalleryViewModel
import com.csugprojects.m5ct.navigation.AppNavigation
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

// =========================================================================
// 1. Permissions List (Centralized definition)
// =========================================================================

// M5: Defines the list of mandatory permissions for CameraX and MediaStore
val REQUIRED_PERMISSIONS = listOfNotNull(
    Manifest.permission.CAMERA,

    // M3: Storage access for existing images (Scoped Storage handling)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
)

// =========================================================================
// 2. Permission Handler Composable
// =========================================================================

/**
 * The root composable launched by MainActivity.kt. It gates access to the main
 * app navigation based on the runtime status of required permissions (M5).
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionHandler(viewModelFactory: ViewModelProvider.Factory) {
    val permissionState = rememberMultiplePermissionsState(
        permissions = REQUIRED_PERMISSIONS
    )
    val viewModel: GalleryViewModel = viewModel(factory = viewModelFactory)
    val allGranted = permissionState.allPermissionsGranted

    // --- FIX APPLIED: Use LaunchedEffect to handle the state change ---
    // The effect runs when the permission state changes.
    LaunchedEffect(permissionState.permissions) {
        if (permissionState.allPermissionsGranted) {
            // If permissions are newly granted, the UI will recompose,
            // and the 'if (allGranted)' block below will execute AppNavigation.
            println("Permissions granted. Launching navigation.")
        }
    }

    if (allGranted) {
        // This is the correct path for a successful permission grant.
        AppNavigation(viewModel)
    } else {
        // Fallback UI
        PermissionRationaleUI(permissionState)
    }
}

// =========================================================================
// 3. Rationale UI (Composable for Denied State)
// =========================================================================

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PermissionRationaleUI(permissionState: com.google.accompanist.permissions.MultiplePermissionsState) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Permissions Required") }) },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "This gallery requires Camera and Storage access to function (M5). Please grant access to use the features.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                // M5: launchMultiplePermissionRequest is invoked from a non-composable onClick scope
                onClick = { permissionState.launchMultiplePermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Grant Permissions", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
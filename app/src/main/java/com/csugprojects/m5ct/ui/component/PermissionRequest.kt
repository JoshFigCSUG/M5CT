// app/src/main/java/com/csugprojects/m5ct/ui/component/PermissionRequest.kt

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
import com.google.accompanist.permissions.ExperimentalPermissionsApi

// =========================================================================
// 1. Permissions Lists (Centralized definition)
// =========================================================================

// Permissions needed for the Camera feature (Will be checked in CameraScreen)
val CAMERA_PERMISSIONS = listOfNotNull(
    Manifest.permission.CAMERA
)

// Permissions needed for reading/writing local files (MediaStore) (Checked in GalleryScreen)
val STORAGE_PERMISSIONS = listOfNotNull(
    // M3: Storage access for reading existing images
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    },

    // FIX: Add WRITE_EXTERNAL_STORAGE for pre-Q devices (minSdk 24 to 28)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    } else null
)

// =========================================================================
// 2. Permission Handler Composable (REMOVED: The root PermissionHandler is deleted)
// =========================================================================

/*
 * The root PermissionHandler composable is REMOVED as requested.
 * It is no longer needed since permissions are deferred to individual screens.
 */


// =========================================================================
// 3. Rationale UI (Composable for Denied State) - KEPT as a utility
// =========================================================================

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PermissionRationaleUI( // Changed visibility from private to public/default
    permissionState: com.google.accompanist.permissions.MultiplePermissionsState,
    featureName: String
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("$featureName Permissions Required") }) },
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
                "The $featureName feature requires necessary permissions to function. Please grant access.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { permissionState.launchMultiplePermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Grant Permissions", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}
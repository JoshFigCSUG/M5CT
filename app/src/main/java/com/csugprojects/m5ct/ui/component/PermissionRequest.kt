package com.csugprojects.m5ct.ui.component

import android.Manifest
import android.os.Build

/**
 * Defines the necessary permissions for camera access.
 * This directly supports the CameraX integration requirement for the assignment.
 */
val CAMERA_PERMISSIONS = listOfNotNull(
    Manifest.permission.CAMERA
)

/**
 * Defines the required permissions for accessing device storage (MediaStore).
 * The list dynamically adjusts based on the Android version (API 33+ vs. older versions)
 * to handle runtime permissions robustly across different devices.
 * This fulfills the assignment's requirement for runtime permissions handling.
 */
val STORAGE_PERMISSIONS = listOfNotNull(
    // Uses READ_MEDIA_IMAGES for Android 13 (Tiramisu) and newer.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        // Falls back to the older READ_EXTERNAL_STORAGE permission for compatibility.
        Manifest.permission.READ_EXTERNAL_STORAGE
    },

    // Includes WRITE_EXTERNAL_STORAGE specifically for legacy devices (API 24 up to Q-1)
    // to ensure proper file persistence using the older storage method.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    } else null
)
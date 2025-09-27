package com.csugprojects.m5ct.navigation

/**
 * Defines all constants (routes) used for navigation within the NavHost.
 * This centralizes routing strings for safety and consistency.
 */
object Routes {
    // Main Photo Grid (Home Screen - M5 Gallery)
    const val GALLERY = "gallery_screen"

    // Camera Capture Screen (M5 Multimedia)
    const val CAMERA = "camera_screen"

    // Single Photo Detail View
    const val DETAIL = "detail_screen"

    // Note: Permission handling is managed outside the NavHost in MainActivity.kt
}
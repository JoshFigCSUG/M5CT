package com.csugprojects.m5ct.data.model

import android.net.Uri

// =========================================================================
// 1. DOMAIN MODEL: Used by the ViewModel and UI
// =========================================================================

/**
 * Represents a single photo item displayed in the Gallery.
 * Used consistently across the UI, whether sourced locally or remotely.
 */
data class GalleryItem(
    val id: String,
    val regularUrl: String, // URL/URI for display in the grid
    val fullUrl: String,    // URL/URI for detail view (higher resolution)
    val description: String?,
    val isLocal: Boolean = false // True if fetched from MediaStore
)

// =========================================================================
// 2. NETWORK MODELS (DTOs): Used by Retrofit/Serialization (M4)
// =========================================================================

/**
 * Top-level response structure for the Unsplash API search endpoint.
 * [kotlinx.serialization.Serializable] is required for JSON parsing.
 */
@kotlinx.serialization.Serializable
data class UnsplashResponse(
    // Unsplash search API returns results in an array named 'results'
    val results: List<UnsplashPhotoDto>
)

/**
 * Data Transfer Object (DTO) for a single photo object returned by the API.
 */
@kotlinx.serialization.Serializable
data class UnsplashPhotoDto(
    val id: String,
    val description: String?,
    val urls: PhotoUrls // Nested object for various image resolutions
)

/**
 * Nested structure to hold different image resolution links.
 */
@kotlinx.serialization.Serializable
data class PhotoUrls(
    val regular: String, // Suitable for grid view
    val full: String     // Suitable for detail view
)

// =========================================================================
// 3. MAPPING: Converts DTO to Domain Model
// =========================================================================

/**
 * Converts a network-specific DTO into the clean domain-level GalleryItem.
 */
fun UnsplashPhotoDto.toGalleryItem(): GalleryItem {
    return GalleryItem(
        id = this.id,
        regularUrl = this.urls.regular,
        fullUrl = this.urls.full,
        description = this.description,
        isLocal = false
    )
}
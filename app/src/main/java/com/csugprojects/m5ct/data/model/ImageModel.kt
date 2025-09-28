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
    val isLocal: Boolean = false, // True if fetched from MediaStore

    // NEW METADATA FIELDS:
    val filename: String? = null,
    val fileSize: String? = null,

    // NEW EXIF FIELDS
    val pixelSize: String? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val exposureTime: String? = null,
    val aperture: String? = null,
    val focalLength: String? = null,
    val iso: String? = null,

    // NEW LOCATION FIELDS
    val photographer: String? = null,
    val locationName: String? = null,
    val locationCity: String? = null,
    val locationCountry: String? = null
)

// =========================================================================
// 2. NETWORK MODELS (DTOs): Used by Retrofit/Serialization (M4)
// =========================================================================

/**
 * Top-level response structure for the Unsplash API search endpoint.
 */
@kotlinx.serialization.Serializable
data class UnsplashResponse(
    // Unsplash search API returns results in an array named 'results'
    val results: List<UnsplashPhotoDto>
)

/**
 * Data Transfer Object (DTO) for a single photo object returned by the API.
 * Includes nested DTOs for rich metadata.
 */
@kotlinx.serialization.Serializable
data class UnsplashPhotoDto(
    val id: String,
    val description: String?,
    val urls: PhotoUrls,
    val width: Int, // Used for pixel size calculation
    val height: Int, // Used for pixel size calculation
    val user: UserDto, // Needed for photographer name
    val location: PhotoLocationDto? = null, // Optional location data
    val exif: PhotoExifDto? = null // Optional EXIF camera data
)

/**
 * Nested structure to hold different image resolution links.
 */
@kotlinx.serialization.Serializable
data class PhotoUrls(
    val regular: String, // Suitable for grid view
    val full: String     // Suitable for detail view
)

/**
 * Nested structure for photographer details.
 */
@kotlinx.serialization.Serializable
data class UserDto(
    val name: String
)

/**
 * Nested structure for EXIF data.
 */
@kotlinx.serialization.Serializable
data class PhotoExifDto(
    val make: String? = null,
    val model: String? = null,
    val exposure_time: String? = null,
    val aperture: String? = null,
    val focal_length: String? = null,
    val iso: Int? = null
)

/**
 * Nested structure for Location data.
 */
@kotlinx.serialization.Serializable
data class PhotoLocationDto(
    val name: String? = null,
    val city: String? = null,
    val country: String? = null,
    val position: LocationPositionDto? = null // Location coordinates (optional)
)

/**
 * Nested structure for Location coordinates (though unused in final GalleryItem, needed for DTO).
 */
@kotlinx.serialization.Serializable
data class LocationPositionDto(
    val latitude: Double? = null,
    val longitude: Double? = null
)


// =========================================================================
// 3. MAPPING: Converts DTO to Domain Model
// =========================================================================

/**
 * Converts a network-specific DTO into the clean domain-level GalleryItem,
 * mapping all nested metadata fields.
 */
fun UnsplashPhotoDto.toGalleryItem(): GalleryItem {
    return GalleryItem(
        id = this.id,
        regularUrl = this.urls.regular,
        fullUrl = this.urls.full,
        description = this.description,
        isLocal = false,

        // General
        pixelSize = "${this.width}x${this.height}",
        photographer = this.user.name,

        // EXIF Data (Using Elvis Operator for safe access)
        cameraMake = this.exif?.make,
        cameraModel = this.exif?.model,
        exposureTime = this.exif?.exposure_time,
        aperture = this.exif?.aperture,
        focalLength = this.exif?.focal_length,
        iso = this.exif?.iso?.toString(),

        // Location Data
        locationName = this.location?.name,
        locationCity = this.location?.city,
        locationCountry = this.location?.country
    )
}
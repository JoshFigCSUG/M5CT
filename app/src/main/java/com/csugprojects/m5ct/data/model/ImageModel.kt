package com.csugprojects.m5ct.data.model

import kotlinx.serialization.SerialName

// =========================================================================
// 1. DOMAIN MODEL (Used by the Repository, ViewModel, and UI)
// =========================================================================

/**
 * The core data structure representing a photo in the application.
 * This clean model is used everywhere, abstracting whether the photo is local or from the network.
 * This separation is fundamental to the MVVM Model layer.
 */
data class GalleryItem(
    val id: String,
    val regularUrl: String, // URI or URL used for display in the photo grid.
    val fullUrl: String,    // URI or URL used for the detailed, high-resolution view.
    val description: String?,
    val isLocal: Boolean = false, // True if sourced from the device's MediaStore.

    // General Metadata
    val filename: String? = null,
    val fileSize: String? = null,

    // EXIF (Camera) Details
    val pixelSize: String? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val exposureTime: String? = null,
    val aperture: String? = null,
    val focalLength: String? = null,
    val iso: String? = null,

    // Location and Source Details
    val photographer: String? = null,
    val locationName: String? = null,
    val locationCity: String? = null,
    val locationCountry: String? = null
)

// =========================================================================
// 2. NETWORK MODELS (DTOs) - For Retrofit and Kotlin Serialization
// =========================================================================

/**
 * Maps the top-level JSON response structure from the Unsplash search API.
 * This is specific to the third-party API integration required by the assignment.
 */
@kotlinx.serialization.Serializable
data class UnsplashResponse(
    // The search results are contained within this list.
    val results: List<UnsplashPhotoDto>
)

/**
 * Data Transfer Object (DTO) for a single photo received directly from the Unsplash API.
 * This network-specific model is used only for deserialization, not directly by the UI.
 */
@kotlinx.serialization.Serializable
data class UnsplashPhotoDto(
    val id: String,
    val description: String?,
    val urls: PhotoUrls,
    val width: Int,
    val height: Int,
    val user: UserDto,
    val location: PhotoLocationDto? = null,
    val exif: PhotoExifDto? = null
)

/**
 * Nested DTO for image URLs.
 */
@kotlinx.serialization.Serializable
data class PhotoUrls(
    val regular: String,
    val full: String
)

/**
 * Nested DTO for photographer information.
 */
@kotlinx.serialization.Serializable
data class UserDto(
    val name: String
)

/**
 * Nested DTO for EXIF camera data.
 * @SerialName annotations map snake_case API fields to Kotlin's camelCase style.
 */
@kotlinx.serialization.Serializable
data class PhotoExifDto(
    val make: String? = null,
    val model: String? = null,
    @SerialName("exposure_time") // Maps API field to Kotlin property.
    val exposureTime: String? = null,
    val aperture: String? = null,
    @SerialName("focal_length") // Maps API field to Kotlin property.
    val focalLength: String? = null,
    val iso: Int? = null
)

/**
 * Nested DTO for Location details.
 */
@kotlinx.serialization.Serializable
data class PhotoLocationDto(
    val name: String? = null,
    val city: String? = null,
    val country: String? = null,
    val position: LocationPositionDto? = null
)

/**
 * Nested DTO for exact coordinates (unused in the final display).
 */
@kotlinx.serialization.Serializable
data class LocationPositionDto(
    val latitude: Double? = null,
    val longitude: Double? = null
)


// =========================================================================
// 3. MAPPING: Conversion from DTO to Domain Model
// =========================================================================

/**
 * Extension function used by the Repository to convert the network DTO into the clean,
 * UI-ready domain model (GalleryItem). This fulfills the MVVM requirement for data mapping.
 */
fun UnsplashPhotoDto.toGalleryItem(): GalleryItem {
    return GalleryItem(
        id = this.id,
        regularUrl = this.urls.regular,
        fullUrl = this.urls.full,
        description = this.description,
        isLocal = false, // Set to false since this is a network conversion.

        // General Info
        pixelSize = "${this.width}x${this.height}",
        photographer = this.user.name,

        // EXIF Data Mapping
        cameraMake = this.exif?.make,
        cameraModel = this.exif?.model,
        exposureTime = this.exif?.exposureTime,
        aperture = this.exif?.aperture,
        focalLength = this.exif?.focalLength,
        iso = this.exif?.iso?.toString(),

        // Location Data Mapping
        locationName = this.location?.name,
        locationCity = this.location?.city,
        locationCountry = this.location?.country
    )
}
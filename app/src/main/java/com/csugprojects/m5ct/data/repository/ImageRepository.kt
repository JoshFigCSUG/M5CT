package com.csugprojects.m5ct.data.repository

import android.content.Context
import android.net.Uri
import com.csugprojects.m5ct.data.local.MediaStoreDataSource
import com.csugprojects.m5ct.data.remote.UnsplashApi
import com.csugprojects.m5ct.data.model.GalleryItem
import com.csugprojects.m5ct.data.model.UnsplashPhotoDto
import com.csugprojects.m5ct.data.model.toGalleryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * The Repository acts as the single source of truth for all data (Model Layer in MVVM).
 * It combines data from both the local MediaStore and the remote Unsplash API.
 */
class ImageRepository(
    private val api: UnsplashApi, // Remote data source (Unsplash API)
    private val mediaStore: MediaStoreDataSource // Local data source (device storage)
) {
    /**
     * Fetches a combined flow of local and remote images.
     * The first emission contains local images for a fast initial load.
     * The second emission combines local images with network results (search or random).
     */
    fun getGalleryItems(searchTerm: String = ""): Flow<List<GalleryItem>> = flow {
        // 1. Fetch and immediately send local images.
        val localImages = mediaStore.getLocalImages()
        emit(localImages)

        try {
            // Determine whether to perform a search or fetch random images.
            val rawNetworkResults: List<UnsplashPhotoDto> = if (searchTerm.isBlank()) {
                api.getRandomPhotos(count = 30)
            } else {
                val searchResponse = api.searchPhotos(query = searchTerm)
                searchResponse.results
            }

            // 2. Map the raw network data (DTOs) to the clean domain model (GalleryItem).
            val networkImages = rawNetworkResults.map { it.toGalleryItem() }

            // 3. Combine local and network images and emit the final list.
            emit(localImages + networkImages)
        } catch (e: Exception) {
            println("Network error: Failed to fetch photos for search term '$searchTerm'. Displaying local images only. Error: ${e.message}")
        }
    }.flowOn(Dispatchers.IO) // Ensures all blocking I/O (local and network) runs on the background thread.

    /**
     * Delegates saving a newly captured photo file from the CameraX component to local storage.
     * This is a suspend function to ensure the I/O operation is backgrounded.
     */
    suspend fun saveCapturedPhoto(photoFile: File, displayName: String): Uri? {
        return mediaStore.savePhotoToMediaStore(photoFile, displayName)
    }

    /**
     * Delegates copying a photo from a user-selected URI (e.g., Photo Picker) into the app's permanent storage location.
     */
    suspend fun copyPhotoToAppStorage(context: Context, sourceUri: Uri): Uri? {
        return mediaStore.copyPhotoToAppStorage(context, sourceUri)
    }

    /**
     * Delegates the deletion request for an image owned by the application.
     */
    fun deletePhoto(uri: Uri): Boolean {
        return mediaStore.deletePhotoFromMediaStore(uri)
    }
}
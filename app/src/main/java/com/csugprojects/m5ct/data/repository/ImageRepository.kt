package com.csugprojects.m5ct.data.repository

import android.content.Context
import android.net.Uri
import com.csugprojects.m5ct.data.local.MediaStoreDataSource
import com.csugprojects.m5ct.data.remote.UnsplashApi
import com.csugprojects.m5ct.data.model.GalleryItem
import com.csugprojects.m5ct.data.model.UnsplashPhotoDto // Ensure this is imported
import com.csugprojects.m5ct.data.model.toGalleryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Repository layer that orchestrates data flow from local (MediaStore)
 * and remote (Unsplash API) sources. This is the single source of truth (MVVM Model Layer).
 */
class ImageRepository(
    private val api: UnsplashApi,
    private val mediaStore: MediaStoreDataSource
) {
    /**
     * Fetches data: If [searchTerm] is empty, fetches random photos. Otherwise, performs a search.
     * This combines local (app-owned) images with remote images.
     */
    fun getGalleryItems(searchTerm: String = ""): Flow<List<GalleryItem>> = flow {
        // 1. Fetch and emit local images immediately (for quick UI load)
        val localImages = mediaStore.getLocalImages()
        emit(localImages)

        try {
            // Use a variable to hold the list of raw DTOs received from the network
            val rawNetworkResults: List<UnsplashPhotoDto> = if (searchTerm.isBlank()) {
                // If the search term is empty, fetch a random set of photos.
                // Assumes api.getRandomPhotos returns List<UnsplashPhotoDto>
                api.getRandomPhotos(count = 30)
            } else {
                // If a term is present, use the Search endpoint.
                val searchResponse = api.searchPhotos(query = searchTerm)
                searchResponse.results
            }

            // 2. Map the list of DTOs to the final domain model (List<GalleryItem>) once
            val networkImages = rawNetworkResults.map { it.toGalleryItem() }

            // 3. Combine and emit the final list (local + network)
            emit(localImages + networkImages)
        } catch (e: Exception) {
            println("Network error: Failed to fetch photos for search term '$searchTerm'. Displaying local images only. Error: ${e.message}")
            // Fallback to showing only local images (already emitted in step 1)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Proxies the save operation for CameraX captures.
     */
    suspend fun saveCapturedPhoto(photoFile: File, displayName: String): Uri? {
        return mediaStore.savePhotoToMediaStore(photoFile, displayName)
    }

    /**
     * Handles copying a Photo Picker URI into the app's permanent storage.
     */
    suspend fun copyPhotoToAppStorage(context: Context, sourceUri: Uri): Uri? {
        return mediaStore.copyPhotoToAppStorage(context, sourceUri)
    }

    /**
     * Proxies the delete operation for images owned by the app.
     */
    fun deletePhoto(uri: Uri): Boolean {
        return mediaStore.deletePhotoFromMediaStore(uri)
    }
}

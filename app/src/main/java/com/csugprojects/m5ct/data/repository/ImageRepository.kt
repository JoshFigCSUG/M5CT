package com.csugprojects.m5ct.data.repository

import android.content.Context
import android.net.Uri
import com.csugprojects.m5ct.data.local.MediaStoreDataSource
import com.csugprojects.m5ct.data.remote.UnsplashApi
import com.csugprojects.m5ct.data.model.GalleryItem
import com.csugprojects.m5ct.data.model.toGalleryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
     * M4/M3: Fetches data by combining local images (from MediaStore) and remote images (Unsplash).
     * Uses Flow and Coroutines for asynchronous operation.
     */
    fun getGalleryItems(): Flow<List<GalleryItem>> = flow {
        // 1. Emit local images first (quick UI response)
        val localImages = mediaStore.getLocalImages()
        emit(localImages)

        // 2. Fetch network images asynchronously
        try {
            val networkResponse = api.searchPhotos()

            // Map the DTOs to the clean domain model
            val networkImages = networkResponse.results.map { it.toGalleryItem() }

            // 3. Combine and emit the final list
            emit(localImages + networkImages)
        } catch (e: Exception) {
            println("Network error: Failed to fetch Unsplash photos. Displaying local images only.")
            emit(localImages)
        }
    }

    /**
     * M3/M5: Proxies the save operation for CameraX captures.
     */
    suspend fun saveCapturedPhoto(photoFile: File, displayName: String): Uri? {
        return mediaStore.savePhotoToMediaStore(photoFile, displayName)
    }

    /**
     * NEW (Policy Compliant I/O): Handles copying a Photo Picker URI into the app's permanent storage.
     * This makes the copied file deletable by the app.
     */
    suspend fun copyPhotoToAppStorage(context: Context, sourceUri: Uri): Uri? {
        // Delegates the complex I/O (reading stream from source and writing to new file)
        return mediaStore.copyPhotoToAppStorage(context, sourceUri)
    }

    /**
     * Proxies the delete operation for images owned by the app (CameraX and Copied Picker images).
     */
    fun deletePhoto(uri: Uri): Boolean {
        // Delegates the ContentResolver delete call to the DataSource
        return mediaStore.deletePhotoFromMediaStore(uri)
    }
}
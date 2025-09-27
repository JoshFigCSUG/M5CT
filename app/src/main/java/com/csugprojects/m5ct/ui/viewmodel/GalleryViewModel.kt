package com.csugprojects.m5ct.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csugprojects.m5ct.data.model.GalleryItem
import com.csugprojects.m5ct.data.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the UI-related data and state for the main photo gallery screen.
 * This is the ViewModel layer.
 */
class GalleryViewModel(
    val repository: ImageRepository // Public for CameraScreen I/O delegation
) : ViewModel() {

    private val _images = MutableStateFlow<List<GalleryItem>>(emptyList())
    val images: StateFlow<List<GalleryItem>> = _images.asStateFlow()

    private val _selectedImage = MutableStateFlow<GalleryItem?>(null)
    val selectedImage: StateFlow<GalleryItem?> = _selectedImage.asStateFlow()

    init { loadImages() }

    private fun loadImages() {
        viewModelScope.launch {
            // Collecting the flow ensures the combined list (local + remote) is retrieved.
            repository.getGalleryItems().collect { combinedList ->
                _images.value = combinedList
            }
        }
    }

    fun selectImage(item: GalleryItem) {
        _selectedImage.value = item
    }

    /**
     * FIX: Triggers a full data refresh to sync with MediaStore after capture.
     */
    fun handleNewCapturedPhoto(photoUri: Uri) {
        // The newly saved image is now guaranteed to be in the MediaStore,
        // so we force the ViewModel to re-query the single source of truth.
        loadImages()
    }

    /**
     * M3/M5 Policy Compliant Flow: Performs background I/O and returns the results.
     */
    suspend fun processAndCopyPickerUris(context: Context, uris: List<Uri>): List<GalleryItem> {
        // This function runs on Dispatchers.IO.
        val copiedItems = mutableListOf<GalleryItem>()

        for (uri in uris) {
            // This is the I/O call which is now fixed to handle all API levels
            val copiedUri = repository.copyPhotoToAppStorage(context, uri)

            // The data model conversion is still needed here for logical consistency
            copiedUri?.let {
                copiedItems.add(
                    GalleryItem(id = it.toString(), regularUrl = it.toString(), fullUrl = it.toString(), description = "Imported Photo", isLocal = true)
                )
            }
        }

        return copiedItems // Return results for the View to process on the Main Thread
    }

    /**
     * Synchronized method to update the StateFlow with new items.
     * NOTE: This is called by the View *after* the I/O completes on the Main Thread.
     * FIX: Triggers a full data refresh to sync with MediaStore after copy operation.
     */
    fun handleNewPhotoPickerUris(newItems: List<GalleryItem>) {
        // The file copy operation is complete, so we force a full refresh.
        loadImages()
    }

    fun deleteSelectedPhoto() {
        val photoToDelete = _selectedImage.value ?: return
        if (photoToDelete.isLocal && photoToDelete.regularUrl.startsWith("content://")) {
            viewModelScope.launch {
                val success = withContext(Dispatchers.IO) {
                    repository.deletePhoto(photoToDelete.regularUrl.toUri())
                }
                if (success) {
                    // Triggers a full data refresh after deletion
                    loadImages()
                    _selectedImage.value = null
                } else {
                    println("Deletion failed for local photo: ${photoToDelete.id}.")
                }
            }
        } else {
            println("Deletion blocked: Cannot delete remote or non-owned file.")
        }
    }
}
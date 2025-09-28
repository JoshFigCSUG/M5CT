package com.csugprojects.m5ct.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csugprojects.m5ct.data.model.GalleryItem
import com.csugprojects.m5ct.data.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the UI-related data and state for the main photo gallery screen,
 * including handling dynamic search queries and background I/O.
 */
class GalleryViewModel(
    val repository: ImageRepository // Public for CameraScreen I/O delegation
) : ViewModel() {

    private val _images = MutableStateFlow<List<GalleryItem>>(emptyList())
    val images: StateFlow<List<GalleryItem>> = _images.asStateFlow()

    private val _selectedImage = MutableStateFlow<GalleryItem?>(null)
    val selectedImage: StateFlow<GalleryItem?> = _selectedImage.asStateFlow()

    // Defaults to empty string ("") to trigger random photo fetch on startup
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        // CRITICAL: Set up the reactive flow to reload images whenever the search query changes
        _searchQuery
            .debounce(300L)
            .filter { query ->
                // FIX: Allow search if the query is blank (for random images) OR if the query is long enough.
                query.isBlank() || query.length > 2
            }
            .onEach { query -> loadImages(query) }
            .launchIn(viewModelScope)
    }

    // Function to handle fetching both local and remote (search) images
    private fun loadImages(query: String) {
        viewModelScope.launch {
            repository.getGalleryItems(query).collect { combinedList ->
                _images.value = combinedList
            }
        }
    }

    fun selectImage(item: GalleryItem) {
        _selectedImage.value = item
    }

    /**
     * Updates the StateFlow for the search query, which automatically triggers a reload.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Performs background I/O to copy photo picker URIs to permanent app storage.
     */
    suspend fun processAndCopyPickerUris(context: Context, uris: List<Uri>): List<GalleryItem> {
        val copiedItems = mutableListOf<GalleryItem>()

        for (uri in uris) {
            val copiedUri = repository.copyPhotoToAppStorage(context, uri)

            copiedUri?.let {
                copiedItems.add(
                    GalleryItem(id = it.toString(), regularUrl = it.toString(), fullUrl = it.toString(), description = "Imported Photo", isLocal = true)
                )
            }
        }

        return copiedItems
    }

    /**
     * Triggers a full data refresh using the current search term after a CameraX capture.
     */
    fun handleNewCapturedPhoto(photoUri: Uri) {
        loadImages(_searchQuery.value)
    }

    /**
     * Triggers a full data refresh using the current search term after a Photo Picker import.
     */
    fun handleNewPhotoPickerUris(newItems: List<GalleryItem>) {
        loadImages(_searchQuery.value)
    }

    fun deleteSelectedPhoto() {
        val photoToDelete = _selectedImage.value ?: return
        if (photoToDelete.isLocal && photoToDelete.regularUrl.startsWith("content://")) {
            viewModelScope.launch {
                val success = withContext(Dispatchers.IO) {
                    repository.deletePhoto(photoToDelete.regularUrl.toUri())
                }
                if (success) {
                    loadImages(_searchQuery.value)
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
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // StateFlow to trigger the photo picker launch in the UI once
    private val _launchPickerEvent = MutableStateFlow(false)
    val launchPickerEvent: StateFlow<Boolean> = _launchPickerEvent.asStateFlow()

    init {
        _searchQuery
            .debounce(300L)
            .filter { query ->
                query.isBlank() || query.length > 2
            }
            .onEach { query -> loadImages(query) }
            .launchIn(viewModelScope)

        // Removed the internal initialization check. The event is now triggered externally.
    }

    /**
     * NEW: Public function called by a transient screen (like PermissionHandler)
     * to request the Photo Picker to launch on the next screen if the gallery is currently empty.
     */
    fun cuePhotoPickerOnEmptyGallery() {
        if (_images.value.isEmpty()) {
            _launchPickerEvent.value = true
        }
    }

    /**
     * Called by the UI after the picker is launched to reset the event flag.
     */
    fun onPickerLaunched() {
        _launchPickerEvent.value = false
    }

    // Function to handle fetching both local and remote (search) images
    private fun loadImages(query: String) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null

                repository.getGalleryItems(query).collect { combinedList ->
                    _images.value = combinedList

                    // Reset the picker flag if we successfully load images
                    if (combinedList.isNotEmpty()) {
                        _launchPickerEvent.value = false
                    }
                }
            } catch (e: Exception) {
                println("Error during image fetch: ${e.message}")
                _errorMessage.value = "Failed to connect to image service. Check your internet connection or API key."
                if (_images.value.isEmpty()) {
                    _images.value = emptyList()
                }
            }
        }
    }

    fun selectImage(item: GalleryItem) {
        _selectedImage.value = item
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

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

    fun handleNewCapturedPhoto(photoUri: Uri) {
        loadImages(_searchQuery.value)
    }

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
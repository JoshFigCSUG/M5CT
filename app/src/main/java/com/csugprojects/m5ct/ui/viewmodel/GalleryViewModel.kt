// app/src/main/java/com/csugprojects/m5ct/ui/viewmodel/GalleryViewModel.kt

package com.csugprojects.m5ct.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csugprojects.m5ct.data.model.GalleryItem
import com.csugprojects.m5ct.data.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow // NEW IMPORT
import kotlinx.coroutines.flow.SharedFlow // NEW IMPORT
import kotlinx.coroutines.flow.asSharedFlow // NEW IMPORT

/**
 * Manages the UI-related data and state for the main photo gallery screen,
 * including handling dynamic search queries and background I/O.
 */
@OptIn(FlowPreview::class)
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

    // NEW: SharedFlow for one-time events, such as signaling successful deletion
    private val _deleteSuccess = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val deleteSuccess: SharedFlow<Boolean> = _deleteSuccess.asSharedFlow()

    init {
        _searchQuery
            .debounce(300L)
            .filter { query ->
                query.isBlank() || query.length > 2
            }
            .onEach { query -> loadImages(query) }
            .launchIn(viewModelScope)
    }

    /**
     * Function to handle fetching both local and remote (search) images.
     */
    fun loadImages(query: String) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null

                repository.getGalleryItems(query).collect { combinedList ->
                    _images.value = combinedList
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

    /**
     * NEW: Performs a local image refresh with retries to account for MediaStore indexing delays.
     */
    fun refreshLocalImagesAfterImport() {
        viewModelScope.launch {
            val maxRetries = 3
            var retryCount = 0
            var imagesLoaded = false

            while (!imagesLoaded && retryCount < maxRetries) {
                try {
                    repository.getGalleryItems(_searchQuery.value).collect { combinedList ->
                        _images.value = combinedList
                        imagesLoaded = true
                    }
                } catch (e: Exception) {
                    println("Error during image refresh: ${e.message}. Retrying...")
                    // Only display final error if retries fail
                    if (retryCount == maxRetries - 1) {
                        _errorMessage.value = "Failed to load newly imported photos after multiple retries."
                    }
                }

                if (!imagesLoaded) {
                    // Wait 500ms before retrying
                    delay(500L)
                    retryCount++
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
        // Use the robust refresh function
        refreshLocalImagesAfterImport()
    }

    fun handleNewPhotoPickerUris(newItems: List<GalleryItem>) {
        // Use the robust refresh function
        refreshLocalImagesAfterImport()
    }

    /**
     * MODIFIED: Launches deletion via viewModelScope and signals success via SharedFlow.
     * The Composable will observe the flow for navigation.
     */
    fun deleteSelectedPhoto() {
        // Use viewModelScope to launch the coroutine tied to the ViewModel's lifecycle
        viewModelScope.launch {
            val photoToDelete = _selectedImage.value ?: return@launch
            if (photoToDelete.isLocal && photoToDelete.regularUrl.startsWith("content://")) {
                val success = withContext(Dispatchers.IO) {
                    repository.deletePhoto(photoToDelete.regularUrl.toUri())
                }
                if (success) {
                    loadImages(_searchQuery.value)
                    _selectedImage.value = null
                    _deleteSuccess.emit(true) // SIGNAL SUCCESS
                } else {
                    println("Deletion failed for local photo: ${photoToDelete.id}.")
                }
            } else {
                println("Deletion blocked: Cannot delete remote or non-owned file.")
            }
        }
    }
}
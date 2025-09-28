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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manages the UI-related data and state for the main photo gallery screen.
 * This class serves as the ViewModel in the MVVM architecture.
 */
@OptIn(FlowPreview::class)
class GalleryViewModel(
    val repository: ImageRepository // The ImageRepository is injected, fulfilling the MVVM and DI requirements.
) : ViewModel() {

    private val _images = MutableStateFlow<List<GalleryItem>>(emptyList())
    val images: StateFlow<List<GalleryItem>> = _images.asStateFlow() // Exposes the list of images as a StateFlow for the UI to observe.

    private val _selectedImage = MutableStateFlow<GalleryItem?>(null)
    val selectedImage: StateFlow<GalleryItem?> = _selectedImage.asStateFlow() // Exposes the currently selected image for the DetailScreen.

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow() // Holds the user's search input.

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow() // Holds network or data loading error messages.

    // SharedFlow for one-time events, signaling successful deletion to trigger navigation.
    private val _deleteSuccess = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val deleteSuccess: SharedFlow<Boolean> = _deleteSuccess.asSharedFlow()

    init {
        // Sets up a search flow that debounces input and filters short queries.
        // This optimizes performance by limiting unnecessary network calls.
        _searchQuery
            .debounce(300L)
            .filter { query ->
                query.isBlank() || query.length > 2
            }
            .onEach { query -> loadImages(query) }
            .launchIn(viewModelScope)
    }

    /**
     * Triggers the ImageRepository to fetch data, supporting both local and remote (search/random) loading.
     */
    fun loadImages(query: String) {
        viewModelScope.launch {
            try {
                _errorMessage.value = null

                // Collects the flow, receiving local images first, then the combined list.
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
     * Refreshes the local image list with retries to account for MediaStore indexing delays.
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
                    if (retryCount == maxRetries - 1) {
                        _errorMessage.value = "Failed to load newly imported photos after multiple retries."
                    }
                }

                if (!imagesLoaded) {
                    delay(500L)
                    retryCount++
                }
            }
        }
    }

    /**
     * Sets the image currently selected by the user.
     */
    fun selectImage(item: GalleryItem) {
        _selectedImage.value = item
    }

    /**
     * Updates the search query input from the UI.
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Processes URIs from the Photo Picker, copies them to app storage via the Repository,
     * and returns the new GalleryItems. Runs on the I/O dispatcher.
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
     * Handles the successful completion of a photo capture operation.
     */
    @Suppress("UNUSED_PARAMETER") // Suppresses the warning for the unused parameter.
    fun handleNewCapturedPhoto(photoUri: Uri) {
        // Triggers a list refresh to ensure the newly saved photo appears in the gallery.
        refreshLocalImagesAfterImport()
    }

    /**
     * Handles the completion of importing photos from the picker.
     */
    @Suppress("UNUSED_PARAMETER") // Suppresses the warning for the unused parameter.
    fun handleNewPhotoPickerUris(newItems: List<GalleryItem>) {
        // Triggers a list refresh to ensure the newly imported photos appear in the gallery.
        refreshLocalImagesAfterImport()
    }

    /**
     * Deletes the currently selected local photo and emits a success signal upon completion.
     */
    fun deleteSelectedPhoto() {
        // Uses viewModelScope to tie the deletion process to the ViewModel's lifecycle.
        viewModelScope.launch {
            val photoToDelete = _selectedImage.value ?: return@launch
            if (photoToDelete.isLocal && photoToDelete.regularUrl.startsWith("content://")) {
                val success = withContext(Dispatchers.IO) {
                    repository.deletePhoto(photoToDelete.regularUrl.toUri())
                }
                if (success) {
                    loadImages(_searchQuery.value)
                    _selectedImage.value = null
                    _deleteSuccess.emit(true) // Signals the UI (DetailScreen) to navigate away.
                } else {
                    println("Deletion failed for local photo: ${photoToDelete.id}.")
                }
            } else {
                println("Deletion blocked: Cannot delete remote or non-owned file.")
            }
        }
    }
}
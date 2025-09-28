package com.csugprojects.m5ct.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.csugprojects.m5ct.data.repository.ImageRepository
import com.csugprojects.m5ct.ui.viewmodel.GalleryViewModel

/**
 * Custom Factory class required to manually inject the ImageRepository dependency into the ViewModel.
 * This satisfies the Dependency Injection (DI) and MVVM architecture requirements.
 */
class GalleryViewModelFactory(
    private val repository: ImageRepository
) : ViewModelProvider.Factory {

    /**
     * Creates and returns the requested ViewModel instance.
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Checks if the requested class is the GalleryViewModel.
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            // Instantiates the ViewModel, passing the necessary ImageRepository.
            @Suppress("UNCHECKED_CAST")
            return GalleryViewModel(repository) as T
        }

        // Throws an error if an unhandled ViewModel class is requested.
        throw IllegalArgumentException("Unknown ViewModel class requested by Factory")
    }
}
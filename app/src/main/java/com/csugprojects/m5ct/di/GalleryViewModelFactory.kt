package com.csugprojects.m5ct.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.csugprojects.m5ct.data.repository.ImageRepository // Dependency
import com.csugprojects.m5ct.ui.viewmodel.GalleryViewModel // ViewModel target

/**
 * Custom Factory class (Manual Dependency Injection) for GalleryViewModel.
 * This pattern allows the Android system to correctly instantiate the ViewModel,
 * providing the required ImageRepository in its constructor.
 */
class GalleryViewModelFactory(
    private val repository: ImageRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Checks if the requested ViewModel class is the GalleryViewModel
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            // Instantiates the ViewModel, injecting the ImageRepository
            @Suppress("UNCHECKED_CAST")
            return GalleryViewModel(repository) as T
        }

        // Throws an error if the Factory is asked to create an unknown ViewModel
        throw IllegalArgumentException("Unknown ViewModel class requested by Factory")
    }
}
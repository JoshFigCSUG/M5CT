package com.csugprojects.m5ct.data.remote

import com.csugprojects.m5ct.data.model.UnsplashResponse
import com.csugprojects.m5ct.data.model.UnsplashPhotoDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface defining the network endpoints for the Unsplash API.
 * This class serves as the remote data source within the MVVM Model layer.
 * It is responsible for fulfilling the assignment's requirement to integrate a third-party API.
 */
interface UnsplashApi {

    /**
     * Searches for photos on Unsplash based on a specific query string.
     */
    @GET("search/photos")
    suspend fun searchPhotos(
        @Query("query") query: String, // The user's search term.
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("client_id") clientId: String = "5yGLJ6FLVF0ST0QcVxwE3YIv3h9-NvCCEQZAsVKR4FI" // API key for authentication.
    ): UnsplashResponse

    /**
     * Fetches a list of random photos when no search query is provided.
     */
    @GET("photos/random")
    suspend fun getRandomPhotos(
        @Query("count") count: Int = 30, // The number of random photos to retrieve.
        @Query("client_id") clientId: String = "5yGLJ6FLVF0ST0QcVxwE3YIv3h9-NvCCEQZAsVKR4FI"
    ): List<UnsplashPhotoDto>
}
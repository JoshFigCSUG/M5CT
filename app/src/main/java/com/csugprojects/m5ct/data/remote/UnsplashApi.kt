package com.csugprojects.m5ct.data.remote

import com.csugprojects.m5ct.data.model.UnsplashResponse
import com.csugprojects.m5ct.data.model.UnsplashPhotoDto
import retrofit2.http.GET
import retrofit2.http.Query

// Retrofit Interface for fetching external data
interface UnsplashApi {
    // 1. Search endpoint (used when query is NOT blank)
    @GET("search/photos")
    suspend fun searchPhotos(
        @Query("query") query: String, // Required parameter
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 30,
        @Query("client_id") clientId: String = "5yGLJ6FLVF0ST0QcVxwE3YIv3h9-NvCCEQZAsVKR4FI"
    ): UnsplashResponse

    // 2. Random endpoint (used when query IS blank)
    @GET("photos/random")
    suspend fun getRandomPhotos(
        @Query("count") count: Int = 30, // Fetches a set number of random photos
        @Query("client_id") clientId: String = "5yGLJ6FLVF0ST0QcVxwE3YIv3h9-NvCCEQZAsVKR4FI"
    ): List<UnsplashPhotoDto> // NOTE: This returns a list of DTOs directly
}
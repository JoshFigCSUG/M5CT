package com.csugprojects.m5ct.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

// Base URL for the Unsplash API
private const val BASE_URL = "https://api.unsplash.com/"

/**
 * Singleton object that creates and holds the concrete Retrofit service instance.
 * This is the actual implementation of the UnsplashApi interface (M4: Networking).
 */
object UnsplashApiService {

    // Configure the JSON parser for kotlinx.serialization
    private val json = Json {
        ignoreUnknownKeys = true // Ignore fields in JSON not present in Kotlin data classes
    }

    // Build the Retrofit client instance
    private val retrofit = Retrofit.Builder()
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .baseUrl(BASE_URL)
        .build()

    /**
     * Public property to expose the concrete implementation of the UnsplashApi interface.
     * This is what gets passed to the ImageRepository.
     * Lazy initialization ensures the Retrofit object is only built when first accessed.
     */
    val api: UnsplashApi by lazy {
        retrofit.create(UnsplashApi::class.java)
    }
}
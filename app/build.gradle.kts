plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
}

android {
    namespace = "com.csugprojects.m5ct"
    compileSdk = 36 // Already set to 36

    defaultConfig {
        applicationId = "com.csugprojects.m5ct"
        minSdk = 24
        targetSdk = 36 // Already set to 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // CRITICAL FIX: Update to Java 17 for API 36 compatibility
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // Updated from 11
        targetCompatibility = JavaVersion.VERSION_17 // Updated from 11
    }
    kotlinOptions {
        jvmTarget = "17" // Updated from 11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    // M5: Image Loading and Caching (Coil)
    implementation(libs.coil.compose)

    // M5: Multimedia Integration (CameraX)
    // CameraX core library using the camera2 implementation
    val camerax_version = "1.5.0"
    // The following line is optional, as the core library is included indirectly by camera-camera2
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    //
    // If you want to additionally use the CameraX Lifecycle library
    implementation(libs.androidx.camera.lifecycle)
    // If you want to additionally use the CameraX VideoCapture library
    implementation(libs.androidx.camera.video)
    // If you want to additionally use the CameraX View class
    implementation(libs.androidx.camera.view)
    // If you want to additionally add CameraX ML Kit Vision Integration
    implementation(libs.androidx.camera.mlkit.vision)
    // If you want to additionally use the CameraX Extensions library
    implementation(libs.androidx.camera.extensions)

    // M4: Networking (Retrofit and Serialization for Unsplash API)

    // Updated to use the modern libs convention for cleaner version management
    implementation(libs.retrofit) // Core Retrofit library (Alias assumed: retrofit.core)
    implementation(libs.retrofit2.kotlinx.serialization.converter) // Converter
    implementation(libs.kotlinx.serialization.json) // JSON runtime

    // M5: Permissions Handling (Accompanist)
    implementation(libs.accompanist.permissions)

    // Compose Navigation
    implementation(libs.androidx.navigation.compose)

}
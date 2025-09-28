import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Defines all plugins required for the Android application, Kotlin, Compose, and Kotlin Serialization.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
}

android {
    namespace = "com.csugprojects.m5ct"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.csugprojects.m5ct"
        minSdk = 24
        targetSdk = 36
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

    // Configures the Java compiler to use Java 17 for modern Android requirements.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Configures the Kotlin compiler to target JVM 17.
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    // Enables Jetpack Compose features for the UI (View Layer).
    buildFeatures {
        compose = true
    }
}

// Defines all external library dependencies required by the project.
dependencies {

    // Base Android and Compose dependencies.
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

    // Image Loading: Coil is used for efficient asynchronous image loading and caching (M5 requirement).
    implementation(libs.coil.compose)

    // Multimedia: CameraX libraries are used for photo capture (M5 requirement).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.androidx.camera.extensions)

    // Networking/Serialization: Retrofit and Kotlin Serialization for Unsplash API integration (M4 requirement).
    implementation(libs.retrofit)
    implementation(libs.retrofit2.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)

    // Permissions: Accompanist is used for robust runtime permission handling (M5 requirement).
    implementation(libs.accompanist.permissions)

    // Navigation: Compose Navigation handles all screen transitions.
    implementation(libs.androidx.navigation.compose)

}
package com.csugprojects.m5ct.data.local

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.csugprojects.m5ct.data.model.GalleryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

// This class is the data source for local files (Model Layer in MVVM).
// It handles all direct interaction with the Android MediaStore for persistence.
class MediaStoreDataSource(private val context: Context) {

    // Defines the dedicated subfolder inside the Pictures directory for this app's photos.
    private val appMediaSubfolder = "/MyAppGallery"

    private val scopedRelativePathValue = appMediaSubfolder.trimStart('/').trimEnd('/')

    // Defines the full path used for modern (Q+) storage access.
    private val canonicalRelativePath = "${Environment.DIRECTORY_PICTURES}/${scopedRelativePathValue}/"

    // Defines which columns (data fields) to retrieve when querying the MediaStore database.
    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.MediaColumns.RELATIVE_PATH, // For modern Android (API 29+)
        MediaStore.MediaColumns.DATA // For legacy Android (API < 29)
    )

    // Function to fetch all photos created and owned by this application.
    // This supports the core Gallery feature of the assignment.
    fun getLocalImages(): List<GalleryItem> {
        val imageList = mutableListOf<GalleryItem>()

        // Chooses the correct storage location URI based on the device's Android version.
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val selection: String?
        val selectionArgs: Array<String>?

        // Uses the appropriate filtering method based on the Android version (Scoped Storage vs. Legacy).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Images.Media.RELATIVE_PATH}=?"
            selectionArgs = arrayOf(canonicalRelativePath)
        } else {
            @Suppress("DEPRECATION")
            val legacyDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val fullLegacyPath = File(legacyDir, scopedRelativePathValue).absolutePath

            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("${fullLegacyPath}%")
        }


        try {
            // Executes the database query and processes the results one by one.
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn)
                        val contentUri = ContentUris.withAppendedId(collection, id)

                        // Only adds the image if its URI can actually be accessed (checks for corruption/permission issues).
                        if (isUriAccessible(contentUri)) {
                            imageList.add(
                                GalleryItem(
                                    id = id.toString(),
                                    regularUrl = contentUri.toString(),
                                    fullUrl = contentUri.toString(),
                                    description = name ?: "Local Image",
                                    isLocal = true
                                )
                            )
                        } else {
                            println("Skipping inaccessible image entry: $contentUri")
                        }
                    } catch (e: Exception) {
                        println("Skipping corrupted image entry: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("Error querying local images: ${e.message}")
        }
        return imageList
    }

    // Suspends the coroutine while copying a photo picked from the system's Photo Picker.
    // This runs on a background I/O thread.
    suspend fun copyPhotoToAppStorage(context: Context, sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return@withContext copyPhotoToAppStorageQPlus(context, sourceUri)
            } else {
                return@withContext copyPhotoToAppStorageLegacy(context, sourceUri)
            }
        } catch (e: CancellationException) {
            // Re-throws cancellation exceptions, a coroutine best practice.
            throw e
        } catch (e: Exception) {
            println("Error copying photo to app storage: ${e.message}")
            return@withContext null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun copyPhotoToAppStorageQPlus(context: Context, sourceUri: Uri): Uri? {
        val resolver = context.contentResolver
        val displayName = "IMP_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
        var newUri: Uri? = null

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, canonicalRelativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1) // Marks the file as pending while data is written.
            }

            newUri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)

            if (newUri == null) {
                throw IOException("Failed to create new MediaStore record for copy.")
            }

            // Streams data from the source URI to the new MediaStore entry.
            resolver.openInputStream(sourceUri)?.use { inputStream ->
                resolver.openOutputStream(newUri)?.use { outputStream ->
                    // Uses a cancellable loop for robust I/O handling, stopping if the coroutine is cancelled.
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (coroutineContext.ensureActive().let { read = inputStream.read(buffer); read >= 0 }) {
                        outputStream.write(buffer, 0, read)
                    }
                } ?: throw IOException("Failed to open output stream for MediaStore URI.")
            } ?: throw IOException("Failed to open input stream from source URI.")

            // Clears the pending flag to make the new image visible in the gallery.
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(newUri, contentValues, null, null)

            return newUri

        } catch (e: Exception) {
            println("Error in Q+ photo copy: ${e.message}")
            if (newUri != null) resolver.delete(newUri, null, null) // Cleans up incomplete entry.
            return null
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun copyPhotoToAppStorageLegacy(context: Context, sourceUri: Uri): Uri? {
        val resolver = context.contentResolver
        val displayName = "IMP_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"

        val legacyDir = getLegacySaveDir()
        val targetFile = File(legacyDir, displayName)
        val targetFilePath = targetFile.absolutePath

        // Streams data directly to a local file path.
        resolver.openInputStream(sourceUri)?.use { inputStream ->
            FileOutputStream(targetFile).use { outputStream ->
                // Uses a cancellable loop for robust I/O handling.
                val buffer = ByteArray(8192)
                var read: Int
                while (coroutineContext.ensureActive().let { read = inputStream.read(buffer); read >= 0 }) {
                    outputStream.write(buffer, 0, read)
                }
            }
        } ?: return null

        // Inserts the file path into MediaStore to make it visible.
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.DATA, targetFilePath)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.SIZE, targetFile.length())
            put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, scopedRelativePathValue)
        }

        val newUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        // Broadcasts an intent to update the gallery immediately on older devices.
        if (newUri != null) {
            context.sendBroadcast(
                Intent(
                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    newUri
                )
            )
        }

        return newUri
    }

    @Suppress("DEPRECATION")
    // Saves a photo captured using the CameraX feature.
    suspend fun savePhotoToMediaStore(photoFile: File, displayName: String): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = "image/jpeg"
        var imageUri: Uri? = null
        val isQPlus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        // Determines the correct content URI for saving.
        val imageCollection = if (isQPlus) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        @Suppress("DEPRECATION")
        val targetFile: File? = if (!isQPlus) {
            val legacyDir = getLegacySaveDir()
            File(legacyDir, photoFile.name)
        } else null

        try {
            // Creates the MediaStore entry, setting metadata and handling API differences.
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Images.Media.SIZE, photoFile.length())

                if (isQPlus) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, canonicalRelativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                } else {
                    put(MediaStore.Images.Media.DATA, targetFile!!.absolutePath)
                    put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, scopedRelativePathValue)
                }
            }

            imageUri = resolver.insert(imageCollection, contentValues)

            if (imageUri == null) {
                throw IOException("Failed to create new MediaStore record.")
            }

            // Handles the physical file write operation.
            if (isQPlus) {
                resolver.openOutputStream(imageUri)?.use { outputStream ->
                    FileInputStream(photoFile).use { inputStream ->
                        // Uses a cancellable I/O loop for robust file transfer.
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (coroutineContext.ensureActive().let { read = inputStream.read(buffer); read >= 0 }) {
                            outputStream.write(buffer, 0, read)
                        }
                    }
                }

                // Marks the file as complete for modern devices.
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)

            } else {
                // Copies the temporary file to the final public path for legacy devices.
                photoFile.copyTo(targetFile!!, overwrite = true)
                // Notifies the system to scan the new file.
                context.sendBroadcast(
                    Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        imageUri
                    )
                )
            }

            // Deletes the temporary CameraX file after saving.
            if (photoFile.exists()) photoFile.delete()

            return@withContext imageUri
        } catch (e: Exception) {
            println("Error saving captured photo: ${e.message}")
            // Cleans up any incomplete files or MediaStore entries on failure.
            if (imageUri != null) resolver.delete(imageUri, null, null)
            if (photoFile.exists()) photoFile.delete()
            targetFile?.delete()
            return@withContext null
        }
    }

    // Deletes an app-owned photo from the device's shared storage.
    fun deletePhotoFromMediaStore(uri: Uri): Boolean {
        println("Attempting to delete: $uri")

        try {
            // Checks if the URI is valid before attempting deletion.
            if (!isUriAccessible(uri)) {
                println("URI not accessible for deletion, treating as already deleted or corrupted: $uri")
                return false
            }

            val result = context.contentResolver.delete(uri, null, null)
            if (result > 0) {
                println("Successfully deleted image: $uri")
                return true
            } else {
                println("Failed to delete image (no rows affected): $uri")
                return false
            }
        } catch (e: SecurityException) {
            println("Permission denied for deletion, check app permissions: $uri - ${e.message}")
            return false
        } catch (e: Exception) {
            println("Error deleting image: $uri - ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    // Helper function to get the correct save directory for older Android versions (pre-Q).
    @Suppress("DEPRECATION")
    private fun getLegacySaveDir(): File {
        val picDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val finalDir = File(picDir, scopedRelativePathValue)

        // Ensures the directory structure exists.
        if (!finalDir.exists()) {
            finalDir.mkdirs()
        }
        return finalDir
    }

    // Helper function to verify that a content URI points to an accessible file.
    private fun isUriAccessible(uri: Uri): Boolean {
        return try {
            // Attempts to open an input stream; success means the URI is valid.
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (_: Exception) { // Catches exceptions if the file is corrupted or permissions are missing.
            false
        }
    }
}
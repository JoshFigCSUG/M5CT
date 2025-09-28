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

/**
 * Handles all direct I/O interactions with the device's MediaStore (local storage).
 */
class MediaStoreDataSource(private val context: Context) {

    // Define the specific sub-folder where all app-owned media will live
    private val appMediaSubfolder = "/MyAppGallery"

    // The relative path value used for MediaStore.RELATIVE_PATH (API 29+).
    // Result: "MyAppGallery"
    private val scopedRelativePathValue = appMediaSubfolder.trimStart('/').trimEnd('/')

    // CRITICAL FIX: Define the full canonical path including the standard directory and a trailing slash.
    // This value, e.g., "Pictures/MyAppGallery/", must be used for all READ and WRITE operations (API 29+).
    private val canonicalRelativePath = "${Environment.DIRECTORY_PICTURES}/${scopedRelativePathValue}/"

    // Columns we want to retrieve from the MediaStore database
    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.MediaColumns.RELATIVE_PATH, // API 29+
        MediaStore.MediaColumns.DATA // API < 29
    )

    // ====================================================================
    // 1. READ: Fetching App-Owned Images (With Robust Error Handling)
    // ====================================================================

    /**
     * Queries MediaStore specifically for images created/owned by this app,
     * skipping any entries that appear corrupted or inaccessible.
     */
    fun getLocalImages(): List<GalleryItem> {
        val imageList = mutableListOf<GalleryItem>()

        // Determine the correct collection URI based on API level
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val selection: String?
        val selectionArgs: Array<String>?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+ (Q and above): Filter by RELATIVE_PATH
            selection = "${MediaStore.Images.Media.RELATIVE_PATH}=?"
            // CRITICAL FIX: Use the consistent, canonical path with trailing slash for querying.
            selectionArgs = arrayOf(canonicalRelativePath)
        } else {
            // API < 29 (Legacy): Filter by DATA path prefix
            @Suppress("DEPRECATION")
            val legacyDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            // Use scopedRelativePathValue here as File() handles the slashes correctly
            val fullLegacyPath = File(legacyDir, scopedRelativePathValue).absolutePath

            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("${fullLegacyPath}%")
        }


        try {
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

                        // NEW: Validate the URI exists and is accessible before adding
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
                        // Skip corrupted entries, preventing a crash on bad data
                        println("Skipping corrupted image entry: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            println("Error querying local images: ${e.message}")
        }
        return imageList
    }

    // ====================================================================
    // 2. CREATE (Photo Picker Copy)
    // ====================================================================

    /**
     * Delegates to the correct save logic based on the API level.
     */
    suspend fun copyPhotoToAppStorage(context: Context, sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return@withContext copyPhotoToAppStorageQPlus(context, sourceUri)
            } else {
                return@withContext copyPhotoToAppStorageLegacy(context, sourceUri)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Error copying photo to app storage: ${e.message}")
            return@withContext null
        }
    }

    /**
     * [API Q+] Reads data from the Photo Picker source URI and saves it as a new, app-owned file.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyPhotoToAppStorageQPlus(context: Context, sourceUri: Uri): Uri? {
        val resolver = context.contentResolver
        val displayName = "IMP_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"
        var newUri: Uri? = null

        try {
            // 1. Create PENDING MediaStore entry
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                // CRITICAL FIX: Use the consistent, canonical path with trailing slash for writing.
                put(MediaStore.MediaColumns.RELATIVE_PATH, canonicalRelativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1) // Mark as pending
            }

            newUri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)

            if (newUri == null) {
                throw IOException("Failed to create new MediaStore record for copy.")
            }

            // 2. Copy the data stream
            resolver.openInputStream(sourceUri)?.use { inputStream ->
                resolver.openOutputStream(newUri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                } ?: throw IOException("Failed to open output stream for MediaStore URI.")
            } ?: throw IOException("Failed to open input stream from source URI.")

            // 3. Clear PENDING status (makes the file visible)
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(newUri, contentValues, null, null)

            return newUri

        } catch (e: Exception) {
            println("Error in Q+ photo copy: ${e.message}")
            if (newUri != null) resolver.delete(newUri, null, null)
            return null
        }
    }

    /**
     * [API < Q] Reads data from the Photo Picker source URI and saves it using the legacy
     * file path (DATA column) method.
     */
    @Suppress("DEPRECATION")
    private fun copyPhotoToAppStorageLegacy(context: Context, sourceUri: Uri): Uri? {
        val resolver = context.contentResolver
        val displayName = "IMP_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"

        // 1. Get the target directory and file path
        val legacyDir = getLegacySaveDir()
        val targetFile = File(legacyDir, displayName)
        val targetFilePath = targetFile.absolutePath

        // 2. Stream data from the Photo Picker source URI to the new local file
        resolver.openInputStream(sourceUri)?.use { inputStream ->
            FileOutputStream(targetFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: return null // Failed to open input stream

        // 3. Insert the new record into MediaStore (using DATA column)
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.DATA, targetFilePath) // CRITICAL: Legacy path
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.SIZE, targetFile.length())
            // NEW: Explicitly set BUCKET_DISPLAY_NAME for better legacy indexing (Fixes initial deletion crash)
            put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, scopedRelativePathValue)
        }

        val newUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        // NEW: Explicitly trigger the Media Scanner for legacy devices (Fixes initial load delay)
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

    // ====================================================================
    // 3. CREATE (CameraX): Saving a new capture file
    // ====================================================================

    // ... (savePhotoToMediaStore remains the same as its logic is fine,
    // it was already updated in the previous fix to use legacy BUCKET_DISPLAY_NAME and MediaScanner) ...

    /**
     * Saves a temporary CameraX photo file to the external storage via MediaStore.
     * Ensures the temporary file is deleted after successful persistence.
     */
    suspend fun savePhotoToMediaStore(photoFile: File, displayName: String): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = "image/jpeg"
        var imageUri: Uri? = null
        val isQPlus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        // Determine the collection and file info
        val imageCollection = if (isQPlus) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        // Determine the final permanent path for legacy mode
        @Suppress("DEPRECATION")
        val targetFile: File? = if (!isQPlus) {
            val legacyDir = getLegacySaveDir()
            File(legacyDir, photoFile.name)
        } else null

        try {
            // 1. Prepare ContentValues
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Images.Media.SIZE, photoFile.length())

                if (isQPlus) {
                    // Q+: Set relative path and mark as pending
                    // CRITICAL FIX: Use the consistent, canonical path with trailing slash for writing.
                    put(MediaStore.Images.Media.RELATIVE_PATH, canonicalRelativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                } else {
                    // Legacy: Use the absolute path of the final destination file
                    put(MediaStore.Images.Media.DATA, targetFile!!.absolutePath)
                    // Ensure legacy files are properly indexed
                    put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, scopedRelativePathValue)
                }
            }

            imageUri = resolver.insert(imageCollection, contentValues)

            if (imageUri == null) {
                throw IOException("Failed to create new MediaStore record.")
            }

            // 2. Perform file copy/streaming
            if (isQPlus) {
                // Q+: Stream from the temporary file to the MediaStore URI
                resolver.openOutputStream(imageUri)?.use { outputStream ->
                    FileInputStream(photoFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // CRITICAL: Clear PENDING status
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)

            } else {
                // Legacy: Copy temp cache file to permanent storage location
                photoFile.copyTo(targetFile!!, overwrite = true)
                // Ensure media scanner runs after file copy on legacy devices
                context.sendBroadcast(
                    Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        imageUri
                    )
                )
            }

            // 3. Clean up the temporary CameraX file
            if (photoFile.exists()) photoFile.delete()

            return@withContext imageUri
        } catch (e: Exception) {
            println("Error saving captured photo: ${e.message}")
            // Clean up: delete the incomplete MediaStore entry and the temp/target files
            if (imageUri != null) resolver.delete(imageUri, null, null)
            if (photoFile.exists()) photoFile.delete()
            targetFile?.delete()
            return@withContext null
        }
    }


    // ====================================================================
    // 4. DELETE: Deleting an app-owned file (Enhanced Error Handling)
    // ====================================================================

    /**
     * Deletes a photo from the device's shared storage using its Content URI.
     * Includes error handling to prevent application crashes.
     */
    fun deletePhotoFromMediaStore(uri: Uri): Boolean {
        println("Attempting to delete: $uri")

        try {
            // NEW: First verify the URI is valid and accessible before attempting deletion
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
            // Catching general exceptions that might occur due to MediaStore corruption
            println("Error deleting image: $uri - ${e.message}")
            e.printStackTrace()
            return false
        }
    }


    // ====================================================================
    // 5. HELPER FUNCTIONS
    // ====================================================================

    /**
     * Helper to get the absolute path to the app's dedicated media folder for API < 29.
     * Also ensures the directory exists.
     */
    @Suppress("DEPRECATION")
    private fun getLegacySaveDir(): File {
        // Use DIRECTORY_PICTURES in the external public storage
        val picDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        // Use the subfolder name
        val finalDir = File(picDir, scopedRelativePathValue)

        // Create the directory if it doesn't exist (critical for pre-Q)
        if (!finalDir.exists()) {
            finalDir.mkdirs()
        }
        return finalDir
    }

    /**
     * NEW: Checks if a given Content URI is accessible (can be opened for reading).
     * Used to filter out corrupted or inaccessible MediaStore entries.
     */
    private fun isUriAccessible(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
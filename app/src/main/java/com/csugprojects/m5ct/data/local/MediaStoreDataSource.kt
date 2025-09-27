package com.csugprojects.m5ct.data.local

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
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
import java.io.FileOutputStream // New import for legacy file writing
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles all direct I/O interactions with the device's MediaStore (local storage).
 */
class MediaStoreDataSource(private val context: Context) {

    // Define the specific sub-folder where all app-owned media will live
    private val appMediaSubfolder = "/MyAppGallery"
    private val appMediaRelativePath = "${Environment.DIRECTORY_PICTURES}${appMediaSubfolder}/"

    // Columns we want to retrieve from the MediaStore database
    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.MediaColumns.RELATIVE_PATH, // Essential for filtering owned files (API 29+)
        MediaStore.MediaColumns.DATA // Essential for filtering owned files (API < 29)
    )

    // ====================================================================
    // 1. READ: Fetching App-Owned Images (Updated for Filtering)
    // ====================================================================

    /**
     * M3/M5: Queries MediaStore specifically for images created/owned by this app,
     * which are indexed under the Pictures/MyAppGallery folder.
     */
    fun getLocalImages(): List<GalleryItem> {
        val imageList = mutableListOf<GalleryItem>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        // FIX: Update selection logic to support both Scoped Storage (Q+) and legacy paths (< Q)
        val selection: String?
        val selectionArgs: Array<String>?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+ (Q and above): Filter by RELATIVE_PATH
            selection = "${MediaStore.Images.Media.RELATIVE_PATH}=?"
            selectionArgs = arrayOf(appMediaRelativePath)
        } else {
            // API < 29 (Lollipop to Pie): Filter by DATA path prefix
            val legacyDir = getLegacySaveDir().absolutePath
            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("${legacyDir}${File.separator}%")
        }


        context.contentResolver.query(
            collection,
            projection,
            selection, // Filter by our specific sub-folder/path
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)

                imageList.add(
                    GalleryItem(
                        id = id.toString(),
                        regularUrl = contentUri.toString(),
                        fullUrl = contentUri.toString(),
                        description = name ?: "Local Image",
                        isLocal = true
                    )
                )
            }
        }
        return imageList
    }

    // ====================================================================
    // 2. CREATE (Photo Picker Copy): Inserts a non-owned file into MediaStore
    // ====================================================================

    /**
     * NEW: Delegates to the correct save logic based on the API level.
     */
    suspend fun copyPhotoToAppStorage(context: Context, sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Option 1: Scoped Storage (Q+)
            return@withContext copyPhotoToAppStorageQPlus(context, sourceUri)
        } else {
            // Option 2: Legacy File Path (< Q)
            return@withContext copyPhotoToAppStorageLegacy(context, sourceUri)
        }
    }

    /**
     * [API Q+] Reads data from the Photo Picker source URI and saves it as a new, app-owned file
     * indexed in MediaStore using Scoped Storage features (RELATIVE_PATH, IS_PENDING).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyPhotoToAppStorageQPlus(context: Context, sourceUri: Uri): Uri? {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(sourceUri) ?: "image/jpeg"

        // Create a unique file name
        val displayName = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"

        // 1. Metadata for the new image record
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            // CRITICAL: Set the relative path to our dedicated subfolder
            put(MediaStore.MediaColumns.RELATIVE_PATH, appMediaRelativePath.trimEnd('/'))
            put(MediaStore.Images.Media.IS_PENDING, 1) // Mark as pending during write
        }

        val newUri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)

        return try {
            if (newUri != null) {
                // 2. Stream data from the Photo Picker source URI to the new file URI
                resolver.openInputStream(sourceUri)?.use { inputStream ->
                    resolver.openOutputStream(newUri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 3. Finalize the record
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(newUri, contentValues, null, null)

                // Grant persistent read permission to the app for the new URI (since the app owns it)
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                resolver.takePersistableUriPermission(newUri, flags)

                newUri
            } else {
                throw IOException("Failed to create new MediaStore record for copy.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (newUri != null) resolver.delete(newUri, null, null) // Cleanup partial record
            null
        }
    }

    /**
     * [API < Q] Reads data from the Photo Picker source URI and saves it using the legacy
     * file path (DATA column) method.
     */
    private fun copyPhotoToAppStorageLegacy(context: Context, sourceUri: Uri): Uri? {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(sourceUri) ?: "image/jpeg"
        val displayName = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"

        // 1. Get the target directory and file
        val legacyDir = getLegacySaveDir()
        val targetFile = File(legacyDir, displayName)
        val targetFilePath = targetFile.absolutePath

        // 2. Stream data from the Photo Picker source URI to the new local file
        try {
            resolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        // 3. Insert the new record into MediaStore (using DATA column)
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.DATA, targetFilePath) // Legacy path
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.SIZE, targetFile.length())
        }

        // Use the generic external URI for pre-Q
        val newUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        return newUri
    }


    // ====================================================================
    // 3. CREATE (CameraX): Saving a new capture file (Updated for Legacy)
    // ====================================================================

    /**
     * Saves a temporary CameraX photo file to the external storage via MediaStore.
     */
    suspend fun savePhotoToMediaStore(photoFile: File, displayName: String): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = "image/jpeg"
        val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        // Determine the permanent file path/object for legacy mode
        val targetFile: File? = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val legacyDir = getLegacySaveDir()
            File(legacyDir, photoFile.name)
        } else null


        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.SIZE, photoFile.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Save CameraX photos to the same app-owned folder (Q+)
                put(MediaStore.Images.Media.RELATIVE_PATH, appMediaRelativePath.trimEnd('/'))
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                // Legacy: Use the absolute path to the intended public external storage file
                put(MediaStore.Images.Media.DATA, targetFile!!.absolutePath)
            }
        }

        val imageUri = resolver.insert(imageCollection, contentValues)

        return@withContext try {
            if (imageUri != null) {
                // 1. Write the content to the final destination
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Q+: Write to the Content URI stream
                    resolver.openOutputStream(imageUri)?.use { outputStream ->
                        FileInputStream(photoFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    // Finalize the record
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)

                } else {
                    // Legacy: Copy the temporary cache file to the permanent target file
                    photoFile.copyTo(targetFile!!, overwrite = true)
                }

                // 2. Clean up the temporary cache file (for all versions)
                if (photoFile.exists()) photoFile.delete()

                imageUri
            } else {
                throw IOException("Failed to create new MediaStore record.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (imageUri != null) resolver.delete(imageUri, null, null)
            if (photoFile.exists()) photoFile.delete()
            targetFile?.delete() // Delete the permanent file if created but registration failed
            null
        }
    }

    // ====================================================================
    // 4. DELETE: Deleting an app-owned file
    // ====================================================================

    /**
     * M3/M5: Deletes a photo from the device's shared storage using its Content URI.
     */
    fun deletePhotoFromMediaStore(uri: Uri): Boolean {
        // The ContentResolver returns the number of rows deleted.
        return context.contentResolver.delete(uri, null, null) > 0
    }

    // ====================================================================
    // 5. HELPER FUNCTION
    // ====================================================================

    /**
     * Helper to get the absolute path to the app's dedicated media folder for API < 29.
     * Also ensures the directory exists.
     */
    private fun getLegacySaveDir(): File {
        // Use DIRECTORY_PICTURES in the external public storage
        @Suppress("DEPRECATION")
        val picDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        // Use the subfolder name, removing the leading slash
        val finalDir = File(picDir, appMediaSubfolder.trimStart('/'))

        // Create the directory if it doesn't exist (critical for pre-Q)
        if (!finalDir.exists()) {
            finalDir.mkdirs()
        }
        return finalDir
    }
}
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
import java.io.FileOutputStream
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

    // FIX: Define the correct value for RELATIVE_PATH in Scoped Storage (API 29+).
    // It must only be the subfolder name, e.g., "MyAppGallery"
    private val scopedRelativePathValue = appMediaSubfolder.trimStart('/').trimEnd('/')

    // Columns we want to retrieve from the MediaStore database
    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.MediaColumns.RELATIVE_PATH, // API 29+
        MediaStore.MediaColumns.DATA // API < 29
    )

    // ====================================================================
    // 1. READ: Fetching App-Owned Images (FIXED for API < 29 and API Q+)
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
            // FIX: Use generic external URI for pre-Q
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val selection: String?
        val selectionArgs: Array<String>?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+ (Q and above): Filter by RELATIVE_PATH
            selection = "${MediaStore.Images.Media.RELATIVE_PATH}=?"
            // FIX APPLIED: Use the correctly trimmed subfolder name for the value.
            selectionArgs = arrayOf(scopedRelativePathValue)
        } else {
            // FIX: API < 29 (Lollipop to Pie): Filter by DATA path prefix
            val legacyDir = getLegacySaveDir().absolutePath
            // Use the absolute path as the filter base
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
     * Delegates to the correct save logic based on the API level. (FIXED)
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
        val displayName = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            // FIX APPLIED: Use the correctly trimmed subfolder name for RELATIVE_PATH.
            put(MediaStore.MediaColumns.RELATIVE_PATH, scopedRelativePathValue)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val newUri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)

        return try {
            if (newUri != null) {
                resolver.openInputStream(sourceUri)?.use { inputStream ->
                    resolver.openOutputStream(newUri)?.use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(newUri, contentValues, null, null)

                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                resolver.takePersistableUriPermission(newUri, flags)

                newUri
            } else {
                throw IOException("Failed to create new MediaStore record for copy.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (newUri != null) resolver.delete(newUri, null, null)
            null
        }
    }

    /**
     * [API < Q] Reads data from the Photo Picker source URI and saves it using the legacy
     * file path (DATA column) method. (FIXED)
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
                // Use FileOutputStream to write the binary data to the physical file
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
            put(MediaStore.MediaColumns.DATA, targetFilePath) // CRITICAL: Legacy path
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.SIZE, targetFile.length())
        }

        val newUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        return newUri
    }

    // ====================================================================
    // 3. CREATE (CameraX): Saving a new capture file
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
                // FIX APPLIED: Use the correctly trimmed subfolder name for RELATIVE_PATH.
                put(MediaStore.Images.Media.RELATIVE_PATH, scopedRelativePathValue)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                // FIX: Legacy: Use the absolute path of the *final* file
                put(MediaStore.Images.Media.DATA, targetFile!!.absolutePath)
            }
        }

        val imageUri = resolver.insert(imageCollection, contentValues)

        return@withContext try {
            if (imageUri != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Q+: Write to the Content URI stream, then delete temp file
                    resolver.openOutputStream(imageUri)?.use { outputStream ->
                        FileInputStream(photoFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)

                } else {
                    // FIX: Legacy: Copy temp cache file to permanent storage location
                    photoFile.copyTo(targetFile!!, overwrite = true)
                    // No need to write stream, the file copy is enough.
                }

                // Safely delete the temporary file used by CameraX (for all versions)
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

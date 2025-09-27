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

    // Columns we want to retrieve from the MediaStore database
    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.MediaColumns.RELATIVE_PATH // Essential for filtering owned files (API 29+)
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

        // FIX: Only query files that are known to be in our specific folder path
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf("Pictures/MyAppGallery/")


        context.contentResolver.query(
            collection,
            projection,
            selection, // Filter by our specific sub-folder
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
     * Reads data from the Photo Picker source URI and saves it as a new, app-owned file
     * indexed in MediaStore (Option 2).
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun copyPhotoToAppStorage(context: Context, sourceUri: Uri): Uri? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(sourceUri) ?: "image/jpeg"

        // Create a unique file name
        val displayName = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}.jpg"

        // 1. Metadata for the new image record
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            // CRITICAL: Set the relative path to our dedicated subfolder
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + appMediaSubfolder)
            put(MediaStore.Images.Media.IS_PENDING, 1) // Mark as pending during write
        }

        val newUri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), contentValues)

        return@withContext try {
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

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.SIZE, photoFile.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Save CameraX photos to the same app-owned folder
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + appMediaSubfolder)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                put(MediaStore.Images.Media.DATA, photoFile.absolutePath)
            }
        }

        val imageUri = resolver.insert(imageCollection, contentValues)

        return@withContext try {
            if (imageUri != null) {
                resolver.openOutputStream(imageUri)?.use { outputStream ->
                    FileInputStream(photoFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }

                // Safely delete the temporary file used by CameraX
                if (photoFile.exists()) photoFile.delete()

                imageUri
            } else {
                throw IOException("Failed to create new MediaStore record.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            if (imageUri != null) resolver.delete(imageUri, null, null)
            if (photoFile.exists()) photoFile.delete()
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
}
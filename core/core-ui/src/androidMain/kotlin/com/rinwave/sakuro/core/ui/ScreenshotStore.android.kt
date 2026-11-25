package com.rinwave.sakuro.core.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * PNG into Pictures/Sakuro through MediaStore, so gallery apps show it. Android 10+ needs no
 * permission; Android 8–9 asks for WRITE_EXTERNAL_STORAGE (declared with maxSdkVersion 28).
 */
@Composable
actual fun rememberScreenshotStore(): ScreenshotStore {
    val context = LocalContext.current
    val store = remember(context) { AndroidScreenshotStore(context.applicationContext, context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        store.onPermissionResult(granted)
    }
    SideEffect { store.requestPermission = { launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) } }
    return store
}

private class AndroidScreenshotStore(
    private val appContext: Context,
    private val uiContext: Context,
) : ScreenshotStore {
    var requestPermission: () -> Unit = {}
    private var pendingPermission: CompletableDeferred<Boolean>? = null

    override val supported = true

    fun onPermissionResult(granted: Boolean) {
        pendingPermission?.complete(granted)
        pendingPermission = null
    }

    override suspend fun save(image: ImageBitmap, baseName: String): String? {
        if (!hasLegacyWritePermission()) {
            val result = CompletableDeferred<Boolean>().also { pendingPermission = it }
            requestPermission()
            if (!result.await()) return null
        }
        val bitmap = image.asAndroidBitmap()
        return withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveScoped(bitmap, baseName)
                } else {
                    saveLegacy(bitmap, baseName)
                }
            }.getOrNull()?.toString()
        }
    }

    override fun share(uri: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_PNG
            putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        uiContext.startActivity(Intent.createChooser(send, null))
    }

    private fun hasLegacyWritePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            appContext.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private fun saveScoped(bitmap: Bitmap, baseName: String): Uri? {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$baseName.png")
            put(MediaStore.Images.Media.MIME_TYPE, MIME_PNG)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) } == true
        }.getOrDefault(false)
        if (!written) {
            resolver.delete(uri, null, null)
            return null
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }

    @Suppress("DEPRECATION") // Android 8–9: public directories and the DATA column are the only way.
    private fun saveLegacy(bitmap: Bitmap, baseName: String): Uri? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val file = File(dir, "$baseName.png")
        val written = FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        if (!written) {
            file.delete()
            return null
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, file.absolutePath)
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_PNG)
        }
        return appContext.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private companion object {
        const val FOLDER = "Sakuro"
        const val MIME_PNG = "image/png"
        const val PNG_QUALITY = 100
    }
}

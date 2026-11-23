package app.hercules.store.ui.install

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

suspend fun stageLocalZip(context: Context, uri: Uri): File? = withContext(Dispatchers.IO) {
    val name = displayName(context, uri) ?: return@withContext null
    if (!name.endsWith(".zip", ignoreCase = true)) return@withContext null
    val dir = File(context.cacheDir, "local-flash").apply { mkdirs() }
    val target = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
    input.use { source -> target.outputStream().use { source.copyTo(it) } }
    target
}

private fun displayName(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')
}

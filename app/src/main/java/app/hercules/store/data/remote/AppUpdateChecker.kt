package app.hercules.store.data.remote

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.hercules.store.BuildConfig
import app.hercules.store.root.RootExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class AppRelease(val version: String, val notes: String, val downloadUrl: String)

data class AppUpdateState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int = 0,
    val release: AppRelease? = null,
    val status: String = "",
    val error: String? = null
)

class AppUpdateChecker(private val client: OkHttpClient, private val preferences: android.content.SharedPreferences) {
    suspend fun check(manual: Boolean): AppUpdateState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastCheck = preferences.getLong(KEY_LAST_CHECK, 0L)
        if (!manual && now - lastCheck < CHECK_INTERVAL) return@withContext AppUpdateState(status = "checked_recently")
        preferences.edit().putLong(KEY_LAST_CHECK, now).apply()
        try {
            val request = Request.Builder().url(RELEASES_URL).header("Accept", "application/vnd.github+json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val json = JSONObject(response.body?.string().orEmpty())
                if (json.optBoolean("draft") || json.optBoolean("prerelease")) {
                    return@withContext AppUpdateState(status = "up_to_date")
                }
                val version = json.optString("tag_name").removePrefix("v")
                val url = json.optJSONArray("assets")?.let { assets ->
                    (0 until assets.length()).asSequence().map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name").endsWith(".apk", true) }
                        ?.optString("browser_download_url")
                }.orEmpty()
                val release = if (version.isNotBlank() && url.isNotBlank() && isNewer(version, BuildConfig.VERSION_NAME)) {
                    AppRelease(version, json.optString("body"), url)
                } else null
                AppUpdateState(release = release, status = if (release == null) "up_to_date" else "update_available")
            }
        } catch (e: Exception) {
            AppUpdateState(status = "check_failed", error = e.message)
        }
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        fun parts(value: String): Pair<List<Int>, String?> {
            val clean = value.substringBefore('+')
            val core = clean.substringBefore('-')
            return core.split('.').map { it.toIntOrNull() ?: 0 }.let { it to clean.substringAfter('-', "").ifBlank { null } }
        }
        val (a, aPre) = parts(candidate)
        val (b, bPre) = parts(current)
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        return aPre == null && bPre != null
    }

    private companion object {
        const val KEY_LAST_CHECK = "app_update_last_check"
        const val CHECK_INTERVAL = 12 * 60 * 60 * 1000L
        const val RELEASES_URL = "https://api.github.com/repos/vuloid/hercules/releases/latest"
    }
}

class AppUpdateController(
    private val checker: AppUpdateChecker,
    private val rootExecutor: RootExecutor,
    private val client: OkHttpClient,
    private val cacheDir: File,
    private val application: Application
) {
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    suspend fun check(manual: Boolean) {
        mutableState.value = mutableState.value.copy(checking = true, error = null, status = "checking")
        mutableState.value = checker.check(manual).copy(checking = false)
    }

    suspend fun install(release: AppRelease) = withContext(Dispatchers.IO) {
        mutableState.value = mutableState.value.copy(downloading = true, progress = 0, error = null, status = "downloading")
        try {
            val request = Request.Builder().url(release.downloadUrl).build()
            val apk = File(cacheDir, "hercules-update-${release.version}.apk")
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    apk.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            downloaded += count
                            mutableState.value = mutableState.value.copy(progress = if (total > 0) (downloaded * 100 / total).toInt().coerceIn(0, 100) else 0)
                        }
                    }
                }
            }
            val result = rootExecutor.run("pm install -r '${apk.absolutePath.replace("'", "'\\''")}'")
            if (result.exitCode == 0 && result.stdout.contains("Success", true)) {
                mutableState.value = mutableState.value.copy(downloading = false, status = "installed")
            } else {
                openInstaller(apk)
                mutableState.value = mutableState.value.copy(downloading = false, status = "installer_opened")
            }
        } catch (e: Exception) {
            mutableState.value = mutableState.value.copy(downloading = false, error = e.message, status = "download_failed")
        }
    }

    private fun openInstaller(apk: File) {
        val uri: Uri = FileProvider.getUriForFile(application, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        application.startActivity(intent)
    }
}

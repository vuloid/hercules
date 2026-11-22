package app.hercules.store.data.remote

import app.hercules.store.data.model.DownloadState
import app.hercules.store.data.model.ModuleCatalog
import app.hercules.store.data.model.ModuleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException

class ModuleApi(private val client: OkHttpClient) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun fetchCatalog(url: String): List<ModuleItem> = withContext(Dispatchers.IO) {
        json.decodeFromString<ModuleCatalog>(getText(url)).modules
    }

    suspend fun fetchGithubModule(owner: String, repo: String): ModuleItem = withContext(Dispatchers.IO) {
        val base = "https://api.github.com/repos/$owner/$repo"
        val info = json.decodeFromString<GithubRepo>(getText(base))
        val release = try {
            json.decodeFromString<GithubRelease>(getText("$base/releases/latest"))
        } catch (e: IOException) {
            null
        } catch (e: SerializationException) {
            null
        }
        val asset = release?.assets?.firstOrNull { it.name.endsWith(".zip", ignoreCase = true) }
            ?: release?.assets?.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        ModuleItem(
            id = info.name,
            name = info.name,
            author = info.owner.login,
            description = info.description.orEmpty(),
            version = release?.tag.orEmpty(),
            versionCode = 0,
            stars = info.stars,
            updatedAt = info.pushedAt?.let { parseInstant(it) } ?: 0L,
            sizeBytes = asset?.size ?: 0L,
            downloadUrl = asset?.url.orEmpty(),
            repoUrl = info.htmlUrl,
            changelog = release?.body.orEmpty()
        )
    }

    fun download(url: String, target: File): Flow<DownloadState> = flow {
        val partial = File(target.parentFile, target.name + ".part")
        target.parentFile?.mkdirs()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                emit(DownloadState.Failed("HTTP ${response.code}"))
                return@flow
            }
            val total = body.contentLength()
            var received = 0L
            var lastEmit = 0L
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        if (received - lastEmit >= EMIT_STEP) {
                            lastEmit = received
                            emit(DownloadState.Progress(received, total))
                        }
                    }
                }
            }
        }
        if (!partial.renameTo(target)) {
            emit(DownloadState.Failed(null))
            return@flow
        }
        emit(DownloadState.Done(target))
    }.flowOn(Dispatchers.IO).catch { emit(DownloadState.Failed(it.message)) }

    private fun getText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "Hercules")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty response")
        }
    }

    private fun parseInstant(value: String): Long? =
        try {
            Instant.parse(value).toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }

    private companion object {
        const val BUFFER_SIZE = 32 * 1024
        const val EMIT_STEP = 128 * 1024L
    }
}

@Serializable
internal data class GithubRepo(
    val name: String,
    val description: String? = null,
    @SerialName("stargazers_count") val stars: Int = 0,
    @SerialName("pushed_at") val pushedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    val owner: GithubOwner
)

@Serializable
internal data class GithubOwner(val login: String)

@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tag: String,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList()
)

@Serializable
internal data class GithubAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val url: String
)

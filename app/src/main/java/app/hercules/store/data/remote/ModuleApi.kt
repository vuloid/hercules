package app.hercules.store.data.remote

import android.util.Log
import app.hercules.store.data.model.DownloadState
import app.hercules.store.data.model.ModuleCatalog
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.data.model.RootManager
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
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
        isLenient = true
    }

    suspend fun fetchCatalog(url: String): List<ModuleItem> = withContext(Dispatchers.IO) {
        val raw = fetchCatalogText(url)
        val mmrlModules = parseMmrlModules(raw)
        val isMmrl = mmrlModules.any { it.versions.isNotEmpty() && it.downloadUrl.isBlank() }
        if (isMmrl) {
            mmrlModules.mapNotNull(::mapMmrlModule)
        } else {
            json.decodeFromString<ModuleCatalog>(raw).modules
        }
    }

    private fun parseMmrlModules(raw: String): List<MmrlModule> = runCatching {
        val modulesEl = json.parseToJsonElement(raw).jsonObject["modules"] ?: return@runCatching emptyList()
        when (modulesEl) {
            is JsonArray -> modulesEl.mapNotNull { el ->
                runCatching { json.decodeFromJsonElement<MmrlModule>(el) }
                    .onFailure { Log.w("ModuleApi", "Skipping malformed MMRL module", it) }
                    .getOrNull()
            }
            is JsonObject -> modulesEl.entries.mapNotNull { (key, value) ->
                runCatching {
                    val mod = json.decodeFromJsonElement<MmrlModule>(value)
                    if (mod.id.isBlank()) mod.copy(id = key) else mod
                }.onFailure { Log.w("ModuleApi", "Skipping malformed MMRL module $key", it) }
                    .getOrNull()
            }
            else -> emptyList()
        }
    }.onFailure { Log.w("ModuleApi", "Failed to parse MMRL catalog", it) }
        .getOrDefault(emptyList())

    private fun fetchCatalogText(url: String): String {
        val cleanPath = url.substringBefore('?')
        if (cleanPath.endsWith(".json", ignoreCase = true)) {
            return getText(url)
        }
        val trimmed = cleanPath.trimEnd('/')
        val base = if (trimmed.endsWith("/index.html", ignoreCase = true)) {
            trimmed.dropLast("/index.html".length).trimEnd('/')
        } else {
            trimmed
        }
        return try {
            getText("$base/json/modules.json")
        } catch (_: IOException) {
            getText("$base/modules.json")
        }
    }

    private fun mapMmrlModule(module: MmrlModule): ModuleItem? {
        val latest = module.versions
            .sortedByDescending { it.versionCode }
            .firstOrNull { it.zipUrl.startsWith("https://", ignoreCase = true) }
            ?: return null
        val ts = latest.timestamp.toLong()
        val updatedAt = if (ts > 1_000_000_000_000L) ts else ts * 1000L
        val changelog = if (
            latest.changelog.startsWith("http://", ignoreCase = true) ||
            latest.changelog.startsWith("https://", ignoreCase = true)
        ) "" else latest.changelog
        val managers = mapRootManagers(module.root)
        val lsposed = hasXposedMarker(module.id) || module.categories.any(::hasXposedMarker)
        return ModuleItem(
            id = module.id,
            name = module.name.ifBlank { module.id },
            author = module.author,
            description = module.description,
            version = latest.version,
            versionCode = latest.versionCode,
            updatedAt = updatedAt,
            downloadUrl = latest.zipUrl,
            repoUrl = module.track?.source.orEmpty(),
            managers = managers,
            lsposed = lsposed,
            changelog = changelog
        )
    }

    private fun mapRootManagers(root: MmrlRoot?): List<RootManager> {
        if (root == null) return listOf(RootManager.MAGISK, RootManager.KERNELSU, RootManager.APATCH)
        val result = buildList {
            if (root.magisk != null && root.magisk !is JsonNull) add(RootManager.MAGISK)
            if (root.kernelsu != null && root.kernelsu !is JsonNull) add(RootManager.KERNELSU)
            if (root.apatch != null && root.apatch !is JsonNull) add(RootManager.APATCH)
        }
        return result.ifEmpty { listOf(RootManager.MAGISK, RootManager.KERNELSU, RootManager.APATCH) }
    }

    private fun hasXposedMarker(value: String): Boolean =
        value.contains("xposed", ignoreCase = true) || value.contains("lsposed", ignoreCase = true)

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

@Serializable
private data class MmrlModule(
    val id: String = "",
    val name: String = "",
    val author: String = "",
    val description: String = "",
    val downloadUrl: String = "",
    val categories: List<String> = emptyList(),
    val versions: List<MmrlVersion> = emptyList(),
    val track: MmrlTrack? = null,
    val root: MmrlRoot? = null
)

@Serializable
private data class MmrlRoot(
    val magisk: JsonElement? = null,
    val kernelsu: JsonElement? = null,
    val apatch: JsonElement? = null
)

@Serializable
private data class MmrlVersion(
    val timestamp: Double = 0.0,
    val version: String = "",
    @Serializable(with = FlexibleIntSerializer::class) val versionCode: Int = 0,
    val zipUrl: String = "",
    val changelog: String = ""
)

private object FlexibleIntSerializer : kotlinx.serialization.KSerializer<Int> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "FlexibleInt", kotlinx.serialization.descriptors.PrimitiveKind.INT
    )
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Int) = encoder.encodeInt(value)
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Int {
        val el = (decoder as? kotlinx.serialization.json.JsonDecoder)?.decodeJsonElement()
            ?: return decoder.decodeInt()
        return when (el) {
            is JsonPrimitive -> el.intOrNull ?: el.content.trim().toIntOrNull() ?: 0
            else -> 0
        }
    }
}

@Serializable
private data class MmrlTrack(val source: String = "")

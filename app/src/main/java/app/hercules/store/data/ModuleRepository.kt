package app.hercules.store.data

import app.hercules.store.data.model.CatalogState
import app.hercules.store.data.model.DownloadState
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.data.model.ScriptFile
import app.hercules.store.data.model.ScriptReport
import app.hercules.store.data.remote.ModuleApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

class ModuleRepository(
    private val api: ModuleApi,
    private val catalogUrl: String,
    private val downloadDir: File
) {
    private val _catalog = MutableStateFlow(CatalogState(loading = true))
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun refresh() {
        _catalog.update { it.copy(loading = true, error = null) }
        try {
            val modules = if (catalogUrl.isBlank()) {
                loadBundledCatalog() ?: MockModules.all()
            } else {
                api.fetchCatalog(catalogUrl)
            }
            _catalog.value = CatalogState(modules = modules)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val bundled = loadBundledCatalog()
            if (bundled != null) {
                _catalog.value = CatalogState(modules = bundled)
            } else {
                _catalog.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private suspend fun loadBundledCatalog(): List<ModuleItem>? = withContext(Dispatchers.IO) {
        runCatching {
            val stream = javaClass.classLoader?.getResourceAsStream("modules.json") ?: return@runCatching null
            val raw = stream.bufferedReader().use { it.readText() }
            json.decodeFromString<BundledCatalog>(raw).modules
        }.getOrNull()
    }

    suspend fun lookupGithub(owner: String, repo: String): ModuleItem = api.fetchGithubModule(owner, repo)

    fun download(module: ModuleItem): Flow<DownloadState> {
        val extension = if (module.isApk) "apk" else "zip"
        val target = File(downloadDir, "${safe(module.id)}-${safe(module.version)}.$extension")
        return if (target.isFile && target.length() > 0) {
            flowOf(DownloadState.Done(target))
        } else {
            api.download(module.downloadUrl, target)
        }
    }

    suspend fun inspectScripts(module: ModuleItem): ScriptReport = withContext(Dispatchers.IO) {
        val files = module.scripts.ifEmpty { readScriptsFromArchive(module) }
        ScriptReport(files, ScriptAnalyzer.analyze(files))
    }

    private suspend fun readScriptsFromArchive(module: ModuleItem): List<ScriptFile> {
        if (module.isApk) return emptyList()
        val archive = download(module).filterIsInstance<DownloadState.Done>().firstOrNull()?.file
            ?: error("Download failed")
        return ZipFile(archive).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && isScript(it.name) }
                .map { entry ->
                    val text = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    ScriptFile(entry.name, text.take(MAX_SCRIPT_CHARS))
                }
                .toList()
        }
    }

    private fun isScript(name: String): Boolean =
        (!name.contains('/') && name.endsWith(".sh")) || name == UPDATE_BINARY

    private fun safe(value: String): String = value.replace(UNSAFE, "_")

    @Serializable
    private data class BundledCatalog(val modules: List<ModuleItem> = emptyList())

    private companion object {
        val UNSAFE = Regex("[^A-Za-z0-9._-]")
        const val UPDATE_BINARY = "META-INF/com/google/android/update-binary"
        const val MAX_SCRIPT_CHARS = 65_536
    }
}

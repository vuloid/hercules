package app.hercules.store.data.model

import kotlinx.serialization.Serializable
import java.io.File

@Serializable
enum class RootManager(val displayName: String, val shortName: String) {
    MAGISK("Magisk", "Magisk"),
    KERNELSU("KernelSU", "KSU"),
    APATCH("APatch", "APatch")
}

@Serializable
data class ScriptFile(val name: String, val content: String)

@Serializable
data class ModuleItem(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val version: String,
    val versionCode: Int,
    val stars: Int = 0,
    val updatedAt: Long,
    val sizeBytes: Long = 0,
    val downloadUrl: String,
    val repoUrl: String = "",
    val managers: List<RootManager> = emptyList(),
    val lsposed: Boolean = false,
    val readme: String = "",
    val changelog: String = "",
    val scripts: List<ScriptFile> = emptyList()
) {
    val isApk: Boolean
        get() = downloadUrl.substringBefore('?').endsWith(".apk", ignoreCase = true)

    val distinctDescription: String
        get() = if (description.trim().equals(name.trim(), ignoreCase = true)) "" else description
}

@Serializable
data class ModuleCatalog(val modules: List<ModuleItem>)

data class CatalogState(
    val modules: List<ModuleItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

data class InstalledModule(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Int,
    val author: String,
    val description: String,
    val enabled: Boolean
) {
    val distinctDescription: String
        get() = if (description.trim().equals(name.trim(), ignoreCase = true)) "" else description
}

sealed interface DownloadState {
    data class Progress(val bytes: Long, val total: Long) : DownloadState
    data class Done(val file: File) : DownloadState
    data class Failed(val reason: String?) : DownloadState
}

enum class Severity { LOW, MEDIUM, HIGH }

enum class FindingKind {
    PIPE_TO_SHELL,
    DESTRUCTIVE_DELETE,
    RAW_DISK_WRITE,
    REMOTE_FETCH,
    OBFUSCATED_EXEC,
    SELINUX_DISABLE,
    PERMISSIVE_CHMOD
}

data class Finding(
    val file: String,
    val line: Int,
    val kind: FindingKind,
    val severity: Severity,
    val snippet: String
)

data class ScriptReport(val files: List<ScriptFile>, val findings: List<Finding>)

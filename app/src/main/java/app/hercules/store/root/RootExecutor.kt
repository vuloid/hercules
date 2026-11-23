package app.hercules.store.root

import app.hercules.store.data.model.InstalledModule
import app.hercules.store.data.model.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

sealed interface ShellEvent {
    data class Line(val text: String, val isError: Boolean) : ShellEvent
    data class Exit(val code: Int) : ShellEvent
}

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String)

class RootExecutor {

    suspend fun run(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su").start()
            process.outputStream.bufferedWriter().use {
                it.write(command)
                it.newLine()
                it.write("exit")
                it.newLine()
            }
            val stderr = async { process.errorStream.bufferedReader().use { it.readText() } }
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            ShellResult(process.waitFor(), stdout, stderr.await())
        } catch (e: IOException) {
            ShellResult(EXIT_NO_SU, "", e.message.orEmpty())
        }
    }

    suspend fun isRootAvailable(): Boolean = run("id -u").stdout.trim() == "0"

    suspend fun detectManager(): RootManager? =
        when (run(DETECT_COMMAND).stdout.trim()) {
            "KERNELSU" -> RootManager.KERNELSU
            "APATCH" -> RootManager.APATCH
            "MAGISK" -> RootManager.MAGISK
            else -> null
        }

    suspend fun listInstalledModules(): List<InstalledModule> {
        val live = ModulePropParser.parse(run(listCommand(MODULES_DIR)).stdout)
        val pending = ModulePropParser.parse(run(listCommand(PENDING_DIR)).stdout)
        val liveById = live.associateBy { it.id }
        val merged = LinkedHashMap<String, InstalledModule>()
        live.forEach { merged[it.id] = it }
        pending.forEach { merged[it.id] = it.copy(enabled = liveById[it.id]?.enabled ?: it.enabled) }
        return merged.values.toList()
    }

    suspend fun listInstalledPackages(): Map<String, Int> =
        PackageListParser.parse(run(PACKAGES_COMMAND).stdout)

    fun installModule(manager: RootManager, file: File): Flow<ShellEvent> =
        stream(installCommand(manager, file))

    fun installApk(file: File): Flow<ShellEvent> = stream(apkCommand(file))

    suspend fun reboot() {
        run("reboot")
    }

    private fun installCommand(manager: RootManager, file: File): String {
        if (file.extension.equals("apk", ignoreCase = true)) return apkCommand(file)
        val path = quote(file.absolutePath)
        return when (manager) {
            RootManager.MAGISK -> "magisk --install-module $path"
            RootManager.KERNELSU -> "ksud module install $path"
            RootManager.APATCH -> "apd module install $path"
        }
    }

    private fun apkCommand(file: File): String = "pm install -r ${quote(file.absolutePath)}"

    private fun stream(command: String): Flow<ShellEvent> = channelFlow {
        val process = try {
            ProcessBuilder("su").start()
        } catch (e: IOException) {
            send(ShellEvent.Line(e.message.orEmpty(), true))
            send(ShellEvent.Exit(EXIT_NO_SU))
            return@channelFlow
        }
        coroutineContext.job.invokeOnCompletion { process.destroy() }
        process.outputStream.bufferedWriter().use {
            it.write(command)
            it.newLine()
            it.write("exit")
            it.newLine()
        }
        val out = launch { pump(process.inputStream, false, this@channelFlow) }
        val err = launch { pump(process.errorStream, true, this@channelFlow) }
        out.join()
        err.join()
        send(ShellEvent.Exit(process.waitFor()))
    }.flowOn(Dispatchers.IO)

    private suspend fun pump(stream: InputStream, isError: Boolean, sink: SendChannel<ShellEvent>) {
        try {
            stream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    sink.send(ShellEvent.Line(line, isError))
                }
            }
        } catch (e: IOException) {
            return
        }
    }

    private fun quote(path: String): String = "'" + path.replace("'", "'\\''") + "'"

    private companion object {
        const val EXIT_NO_SU = 127

        const val DETECT_COMMAND =
            "if [ -x /data/adb/ksud ] || command -v ksud >/dev/null 2>&1; then echo KERNELSU; " +
                "elif [ -x /data/adb/apd ] || command -v apd >/dev/null 2>&1; then echo APATCH; " +
                "elif command -v magisk >/dev/null 2>&1; then echo MAGISK; fi"

        const val MODULES_DIR = "/data/adb/modules"
        const val PENDING_DIR = "/data/adb/modules_update"
        const val PACKAGES_COMMAND = "pm list packages --show-versioncode"

        fun listCommand(dir: String): String =
            "for d in $dir/*/; do " +
                "if [ -f \"\$d\"module.prop ]; then " +
                "echo '$MODULE_MARKER'; cat \"\$d\"module.prop; echo; " +
                "if [ -f \"\$d\"disable ]; then echo disabled=true; fi; " +
                "fi; done"
    }
}

internal const val MODULE_MARKER = "@@module"

internal object ModulePropParser {
    fun parse(output: String): List<InstalledModule> =
        output.split(MODULE_MARKER).drop(1).mapNotNull { block ->
            val props = block.lineSequence().mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
            }.toMap()
            val id = props["id"] ?: return@mapNotNull null
            InstalledModule(
                id = id,
                name = props["name"] ?: id,
                version = props["version"].orEmpty(),
                versionCode = props["versionCode"]?.toIntOrNull() ?: 0,
                author = props["author"].orEmpty(),
                description = props["description"].orEmpty(),
                enabled = props["disabled"] != "true"
            )
        }
}

internal object PackageListParser {
    private val line = Regex("""^package:(\S+)(?:\s+versionCode:(\d+))?""")

    fun parse(output: String): Map<String, Int> =
        output.lineSequence().mapNotNull { raw ->
            val match = line.find(raw.trim()) ?: return@mapNotNull null
            val code = match.groupValues[2].toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
            match.groupValues[1] to code
        }.toMap()
}

package app.hercules.store.data

import app.hercules.store.data.model.Finding
import app.hercules.store.data.model.FindingKind
import app.hercules.store.data.model.ScriptFile
import app.hercules.store.data.model.Severity

object ScriptAnalyzer {
    private class Rule(val kind: FindingKind, val severity: Severity, val pattern: Regex)

    private val rules = listOf(
        Rule(FindingKind.PIPE_TO_SHELL, Severity.HIGH, Regex("""\b(curl|wget)\b.*\|\s*(sh|bash|su)\b""")),
        Rule(
            FindingKind.DESTRUCTIVE_DELETE,
            Severity.HIGH,
            Regex("""\brm\s+-[a-zA-Z]*[rR][a-zA-Z]*\s+/(system|data|vendor|product|sdcard|storage)?(\s|/\*|$)""")
        ),
        Rule(FindingKind.RAW_DISK_WRITE, Severity.HIGH, Regex("""\bdd\b.*\bof=/dev/""")),
        Rule(FindingKind.REMOTE_FETCH, Severity.MEDIUM, Regex("""\b(curl|wget)\b.*https?://""")),
        Rule(FindingKind.OBFUSCATED_EXEC, Severity.MEDIUM, Regex("""base64\s+(-d|--decode)|\beval\b""")),
        Rule(FindingKind.SELINUX_DISABLE, Severity.MEDIUM, Regex("""\bsetenforce\s+0\b""")),
        Rule(FindingKind.PERMISSIVE_CHMOD, Severity.LOW, Regex("""\bchmod\b\s+(-R\s+)?0?777\b"""))
    )

    fun analyze(files: List<ScriptFile>): List<Finding> =
        files.flatMap { file ->
            file.content.lineSequence().mapIndexedNotNull { index, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@mapIndexedNotNull null
                rules.firstOrNull { it.pattern.containsMatchIn(line) }?.let {
                    Finding(file.name, index + 1, it.kind, it.severity, line.take(SNIPPET_LIMIT))
                }
            }.toList()
        }.sortedByDescending { it.severity }

    private const val SNIPPET_LIMIT = 160
}

package app.hercules.store.data

import app.hercules.store.data.model.FindingKind
import app.hercules.store.data.model.ScriptFile
import app.hercules.store.data.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptAnalyzerTest {
    @Test
    fun detectsCurlAndWgetPipedToShells() {
        val findings = analyze(
            """
                curl https://example.org/install.sh | sh
                wget -qO- https://example.org/install.sh | bash
                wget https://example.org/install.sh -O - | su
            """.trimIndent()
        )

        assertEquals(3, findings.size)
        assertTrue(findings.all { it.kind == FindingKind.PIPE_TO_SHELL })
        assertTrue(findings.all { it.severity == Severity.HIGH })
    }

    @Test
    fun detectsRecursiveDeletesOfRootAndSensitiveDirectories() {
        val targets = listOf("/", "/system", "/data/*", "/vendor", "/product", "/sdcard", "/storage")
        val findings = analyze(targets.joinToString("\n") { "rm -rf $it" })

        assertEquals(targets.size, findings.size)
        assertTrue(findings.all { it.kind == FindingKind.DESTRUCTIVE_DELETE })
        assertTrue(findings.all { it.severity == Severity.HIGH })
    }

    @Test
    fun detectsDdWritesToDeviceNodes() {
        val finding = analyze("dd if=boot.img of=/dev/block/by-name/boot").single()

        assertEquals(FindingKind.RAW_DISK_WRITE, finding.kind)
        assertEquals(Severity.HIGH, finding.severity)
    }

    @Test
    fun detectsCurlAndWgetRemoteFetches() {
        val findings = analyze(
            """
                curl -o module.zip https://example.org/module.zip
                wget http://example.org/install.sh -O install.sh
            """.trimIndent()
        )

        assertEquals(2, findings.size)
        assertTrue(findings.all { it.kind == FindingKind.REMOTE_FETCH })
        assertTrue(findings.all { it.severity == Severity.MEDIUM })
    }

    @Test
    fun detectsBase64DecodeAndEval() {
        val findings = analyze(
            """
                base64 -d payload.txt
                base64 --decode payload.txt
                eval "${'$'}payload"
            """.trimIndent()
        )

        assertEquals(3, findings.size)
        assertTrue(findings.all { it.kind == FindingKind.OBFUSCATED_EXEC })
        assertTrue(findings.all { it.severity == Severity.MEDIUM })
    }

    @Test
    fun detectsDisablingSelinux() {
        val finding = analyze("setenforce 0").single()

        assertEquals(FindingKind.SELINUX_DISABLE, finding.kind)
        assertEquals(Severity.MEDIUM, finding.severity)
    }

    @Test
    fun detectsPermissiveChmodForms() {
        val findings = analyze(
            """
                chmod 777 /data/local/tmp/file
                chmod 0777 /data/local/tmp/file
                chmod -R 777 /data/local/tmp/tree
                chmod -R 0777 /data/local/tmp/tree
            """.trimIndent()
        )

        assertEquals(4, findings.size)
        assertTrue(findings.all { it.kind == FindingKind.PERMISSIVE_CHMOD })
        assertTrue(findings.all { it.severity == Severity.LOW })
    }

    @Test
    fun cleanMagiskAndKernelSuInstallScriptsDoNotTriggerFindings() {
        val customize = ScriptFile(
            "customize.sh",
            """
                #!/system/bin/sh
                ui_print "- Installing example module"
                if [ "${'$'}KSU" = "true" ]; then
                  ui_print "- KernelSU detected"
                fi
                set_perm "${'$'}MODPATH/system/bin/example" 0 0 0755
                set_perm_recursive "${'$'}MODPATH/system/etc" 0 0 0755 0644
                abort "Unsupported device"
            """.trimIndent()
        )
        val service = ScriptFile(
            "service.sh",
            """
                #!/system/bin/sh
                MODDIR=${'$'}{0%/*}
                until [ "${'$'}(getprop sys.boot_completed)" = "1" ]; do
                  sleep 1
                done
                mkdir -p /data/adb/example
                chmod 755 /data/adb/example
                resetprop -n persist.example.enabled 1
            """.trimIndent()
        )

        assertTrue(ScriptAnalyzer.analyze(listOf(customize, service)).isEmpty())
    }

    @Test
    fun safeNearMissesDoNotTriggerRules() {
        val findings = analyze(
            """
                rm -f /data/adb/modules/example/cache.tmp
                dd if=boot.img of=boot-copy.img
                base64 payload.txt
                setenforce 1
                chmod 755 /data/adb/modules/example/service.sh
                echo evaluate
            """.trimIndent()
        )

        assertTrue(findings.isEmpty())
    }

    @Test
    fun ignoresBlankAndCommentLinesAndKeepsSourceLineNumbers() {
        val findings = analyze(
            """
                #!/system/bin/sh

                # rm -rf /data
                ui_print "ready"
                setenforce 0
            """.trimIndent()
        )

        assertEquals(1, findings.size)
        assertEquals(5, findings.single().line)
        assertEquals(FindingKind.SELINUX_DISABLE, findings.single().kind)
    }

    @Test
    fun returnsOneFindingPerLineOrderedBySeverity() {
        val findings = analyze(
            """
                chmod 777 /data/local/tmp
                wget https://example.org/file.zip -O file.zip
                rm -rf /vendor
            """.trimIndent()
        )

        assertEquals(
            listOf(
                FindingKind.DESTRUCTIVE_DELETE,
                FindingKind.REMOTE_FETCH,
                FindingKind.PERMISSIVE_CHMOD
            ),
            findings.map { it.kind }
        )
    }

    @Test
    fun trimsLinesAndLimitsReportedSnippets() {
        val longCommand = "  echo " + "x".repeat(200) + "  "
        val finding = analyze("  setenforce 0\n$longCommand\n  chmod 777 /tmp/file  ").first()

        assertEquals("setenforce 0", finding.snippet)
        assertFalse(analyze("chmod 777 ${"x".repeat(200)}").single().snippet.length > 160)
    }

    private fun analyze(content: String): List<app.hercules.store.data.model.Finding> =
        ScriptAnalyzer.analyze(listOf(ScriptFile("customize.sh", content)))
}

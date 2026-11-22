package app.hercules.store.data

import app.hercules.store.data.model.ModuleItem
import app.hercules.store.data.model.RootManager
import app.hercules.store.data.model.ScriptFile

object MockModules {
    private const val DAY = 86_400_000L

    private fun ago(days: Int): Long = System.currentTimeMillis() - days * DAY

    private val all3 = listOf(RootManager.MAGISK, RootManager.KERNELSU, RootManager.APATCH)

    fun all(): List<ModuleItem> = listOf(
        ModuleItem(
            id = "playintegrityfix",
            name = "PlayIntegrityFork",
            author = "osm0sis",
            description = "Fork of PlayIntegrityFix that injects a spoofed device fingerprint through Zygisk to restore Play Integrity verdicts.",
            version = "v16",
            versionCode = 16,
            stars = 1870,
            updatedAt = ago(12),
            sizeBytes = 198_000,
            downloadUrl = "https://github.com/osm0sis/PlayIntegrityFork/releases/latest/download/PlayIntegrityFork.zip",
            repoUrl = "https://github.com/osm0sis/PlayIntegrityFork",
            managers = all3,
            readme = """
                Spoofs a certified device fingerprint inside GMS processes so that Play Integrity checks pass on rooted devices.

                Requires a Zygisk implementation (Zygisk Next or built-in Magisk Zygisk). A custom fingerprint can be supplied through custom.pif.json in the module directory.
            """.trimIndent(),
            changelog = "v16\n- Updated the bundled fingerprint\n- Fixed spoofing on Android 15 QPR1\n- Reduced boot-time overhead"
        ),
        ModuleItem(
            id = "tricky_store",
            name = "TrickyStore",
            author = "5ec1cff",
            description = "Keystore attestation helper that serves a configurable keybox and per-app target list to restore hardware-backed verdicts.",
            version = "1.3.0",
            versionCode = 130,
            stars = 1420,
            updatedAt = ago(30),
            sizeBytes = 612_000,
            downloadUrl = "https://github.com/5ec1cff/TrickyStore/releases/latest/download/Tricky-Store.zip",
            repoUrl = "https://github.com/5ec1cff/TrickyStore",
            managers = all3,
            readme = """
                Intercepts keystore attestation requests and answers them using a keybox placed at /data/adb/tricky_store/keybox.xml.

                Packages that should receive the spoofed chain are listed one per line in /data/adb/tricky_store/target.txt.
            """.trimIndent(),
            changelog = "1.3.0\n- Added security patch level override\n- Improved stability on KernelSU\n- Fixed target.txt parsing with trailing spaces",
            scripts = listOf(
                ScriptFile(
                    "service.sh",
                    "#!/system/bin/sh\nuntil [ \"\$(getprop sys.boot_completed)\" = \"1\" ]; do sleep 1; done\nchmod 755 /data/adb/tricky_store\nmkdir -p /data/adb/tricky_store\n"
                ),
                ScriptFile(
                    "customize.sh",
                    "#!/system/bin/sh\nif [ \"\$ZYGISK_ENABLED\" != \"true\" ]; then\n  abort \"Zygisk is required\"\nfi\nui_print \"- Installing Tricky Store\"\n"
                )
            )
        ),
        ModuleItem(
            id = "zygisksu",
            name = "Zygisk Next",
            author = "Dr-TSNG",
            description = "Standalone Zygisk implementation for KernelSU, APatch and Magisk with its own injection and denylist handling.",
            version = "v1.2.9",
            versionCode = 529,
            stars = 2310,
            updatedAt = ago(48),
            sizeBytes = 1_450_000,
            downloadUrl = "https://github.com/Dr-TSNG/ZygiskNext/releases/latest/download/Zygisk-Next.zip",
            repoUrl = "https://github.com/Dr-TSNG/ZygiskNext",
            managers = all3,
            lsposed = true,
            readme = """
                Provides the Zygisk API on root solutions that do not ship it, and replaces Magisk's built-in Zygisk when preferred.

                Required by LSPosed, Shamiko and most Zygisk modules on KernelSU and APatch.
            """.trimIndent(),
            changelog = "v1.2.9\n- Fixed crash on Android 16 beta\n- Updated companion process handling\n- Smaller module size"
        ),
        ModuleItem(
            id = "zygisk_shamiko",
            name = "Shamiko",
            author = "LSPosed",
            description = "Hides root and Zygisk from selected applications by working with the enforced denylist.",
            version = "v1.2.1",
            versionCode = 1021,
            stars = 1190,
            updatedAt = ago(95),
            sizeBytes = 214_000,
            downloadUrl = "https://github.com/LSPosed/LSPosed.github.io/releases/latest/download/Shamiko.zip",
            repoUrl = "https://github.com/LSPosed/LSPosed.github.io",
            managers = all3,
            lsposed = true,
            readme = """
                Works together with the denylist of the active root manager. Disable Magisk's Enforce DenyList and configure the denylist for target applications.
            """.trimIndent(),
            changelog = "v1.2.1\n- Fixed mount namespace leak\n- Improved compatibility with recent kernels"
        ),
        ModuleItem(
            id = "zygisk_lsposed",
            name = "LSPosed",
            author = "JingMatrix",
            description = "Xposed framework implementation on top of Zygisk, allowing runtime hooks without modifying APKs.",
            version = "v1.10.2",
            versionCode = 7285,
            stars = 3640,
            updatedAt = ago(21),
            sizeBytes = 2_700_000,
            downloadUrl = "https://github.com/JingMatrix/LSPosed/releases/latest/download/LSPosed-zygisk.zip",
            repoUrl = "https://github.com/JingMatrix/LSPosed",
            managers = all3,
            lsposed = true,
            readme = """
                Loads Xposed modules into selected application processes. Scope is configured per module from the LSPosed manager.

                Requires Zygisk. Reboot after installation to activate the framework.
            """.trimIndent(),
            changelog = "v1.10.2\n- Android 16 support\n- Fixed module scope persistence\n- Reduced logging overhead"
        ),
        ModuleItem(
            id = "zygisk-assistant",
            name = "Zygisk Assistant",
            author = "snake-4",
            description = "Hides Zygisk injection traces from detection logic without requiring a separate hiding module.",
            version = "v2.1.4",
            versionCode = 214,
            stars = 760,
            updatedAt = ago(420),
            sizeBytes = 305_000,
            downloadUrl = "https://github.com/snake-4/Zygisk-Assistant/releases/latest/download/Zygisk-Assistant.zip",
            repoUrl = "https://github.com/snake-4/Zygisk-Assistant",
            managers = all3,
            readme = "Cleans up mount points and memory mappings left behind by Zygisk in processes on the denylist.",
            changelog = "v2.1.4\n- Fixed rare bootloop on Android 14\n- Minor cleanup",
            scripts = listOf(
                ScriptFile(
                    "customize.sh",
                    "#!/system/bin/sh\nui_print \"- Installing Zygisk Assistant\"\nset_perm_recursive \"\$MODPATH\" 0 0 0755 0644\n"
                )
            )
        )
    )
}

package app.hercules.store.ui.components

import app.hercules.store.data.model.ModuleItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

fun formatCount(value: Int): String =
    if (value >= 1000) String.format(Locale.ROOT, "%.1fk", value / 1000f).replace(".0k", "k") else value.toString()

fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "-"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%d KB", bytes / 1024)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576f)
}

fun formatDate(millis: Long): String = dateFormatter.format(Instant.ofEpochMilli(millis))

fun ModuleItem.compatLabel(): String =
    buildList {
        managers.forEach { add(it.shortName) }
        if (lsposed) add("LSPosed")
    }.joinToString("/")

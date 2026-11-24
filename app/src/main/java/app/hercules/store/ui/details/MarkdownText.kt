package app.hercules.store.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import app.hercules.store.ui.theme.HerculesMono
import app.hercules.store.ui.theme.SileoAccent

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data class Table(val rows: List<List<String>>) : MarkdownBlock
}

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = parseMarkdown(markdown)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    inlineMarkdown(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    color = SileoAccent
                )
                is MarkdownBlock.Paragraph -> Text(inlineMarkdown(block.text), style = MaterialTheme.typography.bodyMedium)
                is MarkdownBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(app.hercules.store.R.string.markdown_bullet), color = SileoAccent, style = MaterialTheme.typography.bodyMedium)
                    Text(inlineMarkdown(block.text), style = MaterialTheme.typography.bodyMedium)
                }
                is MarkdownBlock.Code -> Text(
                    block.text,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .horizontalScroll(rememberScrollState()).padding(12.dp),
                    style = HerculesMono.log,
                    color = MaterialTheme.colorScheme.onSurface
                )
                is MarkdownBlock.Table -> MarkdownTable(block.rows)
            }
        }
    }
}

@Composable
private fun MarkdownTable(rows: List<List<String>>) {
    val columns = rows.maxOfOrNull { it.size } ?: return
    val shape = RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth().border(1.dp, SileoAccent.copy(alpha = 0.4f), shape)) {
        rows.forEachIndexed { index, row ->
            if (row.isNotEmpty() && row.all { it.matches(Regex(":?-{3,}:?")) }) return@forEachIndexed
            Row(Modifier.fillMaxWidth().background(if (index == 0) SileoAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerLow)) {
                repeat(columns) { column ->
                    Text(
                        inlineMarkdown(row.getOrElse(column) { "" }),
                        modifier = Modifier.weight(1f).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)).padding(8.dp),
                        style = if (index == 0) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                        color = if (index == 0) SileoAccent else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun parseMarkdown(input: String): List<MarkdownBlock> {
    val lines = input.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trim()
        if (line.isBlank()) { index++; continue }
        if (line.startsWith("```")) {
            val code = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trim().startsWith("```")) code += lines[index++]
            if (index < lines.size) index++
            blocks += MarkdownBlock.Code(code.joinToString("\n"))
            continue
        }
        val heading = Regex("^(#{1,3})\\s+(.+)$").matchEntire(line)
        if (heading != null) {
            blocks += MarkdownBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
            index++; continue
        }
        if (line.startsWith("|") && line.endsWith("|")) {
            val rows = mutableListOf<List<String>>()
            while (index < lines.size) {
                val tableLine = lines[index].trim()
                if (!tableLine.startsWith("|") || !tableLine.endsWith("|")) break
                rows += tableLine.removePrefix("|").removeSuffix("|").split("|").map(String::trim)
                index++
            }
            blocks += MarkdownBlock.Table(rows)
            continue
        }
        if (Regex("^[-*]\\s+.+").matches(line)) {
            blocks += MarkdownBlock.Bullet(line.drop(2).trim())
            index++; continue
        }
        val paragraph = mutableListOf(line)
        index++
        while (index < lines.size && lines[index].isNotBlank() && !lines[index].trim().startsWith("#") &&
            !lines[index].trim().startsWith("```") && !Regex("^[-*]\\s+").containsMatchIn(lines[index].trim()) &&
            !lines[index].trim().startsWith("|")
        ) paragraph += lines[index++].trim()
        blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" "))
    }
    return blocks
}

@Composable
private fun inlineMarkdown(value: String) = buildAnnotatedString {
    val token = Regex("(\\*\\*.+?\\*\\*)|(`.+?`)")
    var cursor = 0
    token.findAll(value).forEach { match ->
        append(value.substring(cursor, match.range.first))
        val content = match.value
        if (content.startsWith("**")) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(content.removeSurrounding("**")) }
        else withStyle(SpanStyle(fontFamily = HerculesMono.body.fontFamily, color = SileoAccent, background = SileoAccent.copy(alpha = 0.1f))) {
            append(content.removeSurrounding("`"))
        }
        cursor = match.range.last + 1
    }
    append(value.substring(cursor))
}

package app.hercules.store.ui.details

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.hercules.store.R
import app.hercules.store.data.model.Finding
import app.hercules.store.data.model.FindingKind
import app.hercules.store.data.model.ModuleItem
import app.hercules.store.data.model.ScriptFile
import app.hercules.store.data.model.ScriptReport
import app.hercules.store.data.model.Severity
import app.hercules.store.ui.HerculesViewModelFactory
import app.hercules.store.ui.components.AccentButton
import app.hercules.store.ui.components.HerculesShapes
import app.hercules.store.ui.components.ModuleBadges
import app.hercules.store.ui.components.QuietButton
import app.hercules.store.ui.components.formatSize
import app.hercules.store.ui.theme.HerculesMono

private const val PREVIEW_LINES = 80

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ModuleDetailsSheet(
    module: ModuleItem,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    viewModel: DetailsViewModel = viewModel(key = module.id, factory = HerculesViewModelFactory)
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scripts by viewModel.scripts.collectAsStateWithLifecycle()
    var showScripts by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val scheme = MaterialTheme.colorScheme
    val hasDownload = module.downloadUrl.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = scheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(module.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = module.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
            Text(module.id, style = HerculesMono.body, color = scheme.onSurfaceVariant)
            ModuleBadges(module)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AccentButton(
                    text = stringResource(R.string.action_install),
                    onClick = onInstall,
                    enabled = hasDownload
                )
                QuietButton(
                    text = stringResource(if (module.isApk) R.string.action_download_apk else R.string.action_download_zip),
                    onClick = { uriHandler.openUri(module.downloadUrl) },
                    enabled = hasDownload,
                    icon = Icons.Rounded.Download
                )
                QuietButton(
                    text = stringResource(if (showScripts) R.string.action_hide_scripts else R.string.action_inspect_scripts),
                    onClick = {
                        showScripts = !showScripts
                        if (showScripts) viewModel.inspect(module)
                    },
                    enabled = hasDownload || module.scripts.isNotEmpty(),
                    icon = Icons.Rounded.Security
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                InfoRow(stringResource(R.string.label_file_size), formatSize(module.sizeBytes))
                if (module.repoUrl.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { uriHandler.openUri(module.repoUrl) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.label_source),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = module.repoUrl.removePrefix("https://"),
                                style = HerculesMono.label,
                                color = scheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = scheme.primary
                            )
                        }
                    }
                }
            }

            val description = module.readme.ifBlank { module.description }
            if (description.isNotBlank()) {
                Section(stringResource(R.string.section_description)) {
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (module.changelog.isNotBlank()) {
                Section(stringResource(R.string.section_changelog)) {
                    Text(module.changelog, style = HerculesMono.body)
                }
            }
            if (showScripts) {
                Section(stringResource(R.string.section_scripts)) {
                    ScriptsContent(scripts)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = HerculesMono.label)
    }
}

@Composable
private fun ScriptsContent(state: ScriptState) {
    when (state) {
        ScriptState.Idle, ScriptState.Loading -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                text = stringResource(R.string.scripts_loading),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is ScriptState.Failed -> Text(
            text = stringResource(R.string.scripts_failed, state.message.orEmpty()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        is ScriptState.Ready -> ReportContent(state.report)
    }
}

@Composable
private fun ReportContent(report: ScriptReport) {
    if (report.files.isEmpty()) {
        Text(
            text = stringResource(R.string.scripts_none_found),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    if (report.findings.isEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.scripts_clean, report.files.size),
                style = MaterialTheme.typography.bodySmall
            )
        }
    } else {
        report.findings.forEach { FindingRow(it) }
    }
    report.files.forEach { ScriptBlock(it) }
}

@Composable
private fun FindingRow(finding: Finding) {
    val tint = finding.severity.color()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.08f), HerculesShapes.Control)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(finding.severity.labelRes()), style = HerculesMono.label, color = tint)
            Text(
                text = "${finding.file}:${finding.line}",
                style = HerculesMono.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(stringResource(finding.kind.labelRes()), style = MaterialTheme.typography.bodySmall)
        Text(
            text = finding.snippet,
            style = HerculesMono.log,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ScriptBlock(file: ScriptFile) {
    val lines = file.content.lines()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(file.name, style = HerculesMono.body)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background, HerculesShapes.Control)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), HerculesShapes.Control)
                .horizontalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            Text(
                text = lines.take(PREVIEW_LINES).joinToString("\n"),
                style = HerculesMono.log,
                softWrap = false
            )
        }
        if (lines.size > PREVIEW_LINES) {
            Text(
                text = stringResource(R.string.scripts_truncated, PREVIEW_LINES),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Severity.color(): Color = when (this) {
    Severity.HIGH -> MaterialTheme.colorScheme.error
    Severity.MEDIUM -> MaterialTheme.colorScheme.tertiary
    Severity.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
}

@StringRes
private fun Severity.labelRes(): Int = when (this) {
    Severity.HIGH -> R.string.severity_high
    Severity.MEDIUM -> R.string.severity_medium
    Severity.LOW -> R.string.severity_low
}

@StringRes
private fun FindingKind.labelRes(): Int = when (this) {
    FindingKind.PIPE_TO_SHELL -> R.string.finding_pipe_to_shell
    FindingKind.DESTRUCTIVE_DELETE -> R.string.finding_destructive_delete
    FindingKind.RAW_DISK_WRITE -> R.string.finding_raw_disk_write
    FindingKind.REMOTE_FETCH -> R.string.finding_remote_fetch
    FindingKind.OBFUSCATED_EXEC -> R.string.finding_obfuscated_exec
    FindingKind.SELINUX_DISABLE -> R.string.finding_selinux_disable
    FindingKind.PERMISSIVE_CHMOD -> R.string.finding_permissive_chmod
}

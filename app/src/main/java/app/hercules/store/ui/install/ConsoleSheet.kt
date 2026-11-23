package app.hercules.store.ui.install

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.hercules.store.R
import app.hercules.store.ui.components.AccentButton
import app.hercules.store.ui.components.HerculesShapes
import app.hercules.store.ui.components.MonoBadge
import app.hercules.store.ui.components.QuietButton
import app.hercules.store.ui.theme.HerculesMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleSheet(
    state: InstallUiState,
    onDismiss: () -> Unit,
    onReboot: () -> Unit
) {
    val running by rememberUpdatedState(state.running)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target -> target != SheetValue.Hidden || !running }
    )
    val listState = rememberLazyListState()
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.phase) {
        if (state.phase == InstallPhase.SUCCESS || state.phase == InstallPhase.FAILED) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(state.log.size) {
        if (state.log.isNotEmpty()) listState.scrollToItem(state.log.lastIndex)
    }

    ModalBottomSheet(
        onDismissRequest = { if (!running) onDismiss() },
        sheetState = sheetState,
        containerColor = scheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.console_title, state.module.name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                MonoBadge(
                    text = stringResource(state.phase.label),
                    accent = state.phase == InstallPhase.SUCCESS
                )
            }

            if (state.running) {
                val progress = state.progress
                if (state.phase == InstallPhase.DOWNLOADING && progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(scheme.background, HerculesShapes.Card)
                    .border(1.dp, scheme.outlineVariant.copy(alpha = 0.3f), HerculesShapes.Card)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp)
                ) {
                    items(state.log) { entry -> LogLine(entry) }
                }
            }

            if (!state.running) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.phase == InstallPhase.SUCCESS && !state.module.isApk) {
                        AccentButton(
                            text = stringResource(R.string.action_reboot),
                            onClick = onReboot,
                            icon = Icons.Rounded.RestartAlt
                        )
                    }
                    QuietButton(text = stringResource(R.string.action_close), onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun LogLine(entry: LogEntry) {
    val scheme = MaterialTheme.colorScheme
    when (entry) {
        is LogEntry.Shell -> Text(
            text = entry.text,
            style = HerculesMono.log,
            color = if (entry.isError) scheme.error else scheme.onSurface
        )
        is LogEntry.System -> Text(
            text = stringResource(entry.res, *entry.args.toTypedArray()),
            style = HerculesMono.log,
            color = scheme.primary
        )
    }
}

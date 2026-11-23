package app.hercules.store.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@Composable
fun systemNavigationBottomInset(): Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

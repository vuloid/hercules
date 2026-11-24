package app.hercules.store.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import dev.chrisbanes.haze.HazeState
import app.hercules.store.HerculesApp
import app.hercules.store.ui.components.glassSurface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.hercules.store.R
import app.hercules.store.ui.catalog.CatalogScreen
import app.hercules.store.ui.catalog.CatalogViewModel
import app.hercules.store.ui.theme.SileoAccent
import app.hercules.store.ui.install.ConsoleSheet
import app.hercules.store.ui.install.InstallViewModel
import app.hercules.store.ui.install.stageLocalZip
import app.hercules.store.ui.installed.InstalledScreen
import app.hercules.store.ui.status.RootStatusPill
import app.hercules.store.ui.status.RootStatusViewModel
import kotlinx.coroutines.launch
import java.io.IOException
import android.os.Build
import app.hercules.store.BuildConfig

private enum class Destination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Featured("featured", R.string.tab_featured, Icons.Rounded.Star),
    Browse("browse", R.string.tab_catalog, Icons.Rounded.Search),
    Installed("installed", R.string.tab_installed, Icons.Rounded.Inventory2)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerculesRoot() {
    val navController = rememberNavController()
    val installViewModel: InstallViewModel = viewModel(factory = HerculesViewModelFactory)
    val installState by installViewModel.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    var selectedTabRoute by rememberSaveable { mutableStateOf(Destination.Featured.route) }
    LaunchedEffect(currentRoute) {
        currentRoute?.let { selectedTabRoute = it }
    }
    val navigateToTab: (Destination) -> Unit = { destination ->
        selectedTabRoute = destination.route
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val statusViewModel: RootStatusViewModel = viewModel(factory = HerculesViewModelFactory)
    val catalogViewModel: CatalogViewModel = viewModel(factory = HerculesViewModelFactory)
    val rootStatus by statusViewModel.status.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val hazeState = remember { HazeState() }
    val preferences = remember { context.getSharedPreferences("hercules_settings", android.content.Context.MODE_PRIVATE) }
    var liquidGlassEnabled by remember { mutableStateOf(preferences.getBoolean("liquidGlassEnabled", true)) }
    var showSettings by remember { mutableStateOf(false) }
    val invalidZipMessage = stringResource(R.string.flash_invalid_zip)
    val copyFailedMessage = stringResource(R.string.flash_copy_failed)
    val flashPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = try {
                stageLocalZip(context, uri)
            } catch (e: IOException) {
                snackbarHostState.showSnackbar(copyFailedMessage)
                return@launch
            }
            if (file == null) {
                snackbarHostState.showSnackbar(invalidZipMessage)
                return@launch
            }
            installViewModel.installLocal(file)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                            RootStatusPill(rootStatus, modifier = Modifier.weight(1f, fill = false), hazeState = hazeState, glassEnabled = liquidGlassEnabled)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.glassSurface(hazeState, liquidGlassEnabled, RoundedCornerShape(50))
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                        IconButton(onClick = {
                            flashPicker.launch(arrayOf("application/zip", "application/octet-stream"))
                        }) {
                            Icon(
                                Icons.Rounded.FolderZip,
                                contentDescription = stringResource(R.string.action_flash_local)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.background)
                )
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 42.dp, vertical = 5.dp)
                    .height(58.dp)
                    .glassSurface(hazeState, liquidGlassEnabled, RoundedCornerShape(32.dp))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Destination.entries.forEach { destination ->
                    val selected = selectedTabRoute == destination.route
                    val tint = if (selected) SileoAccent else scheme.onSurfaceVariant
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable {
                            if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            navigateToTab(destination)
                        }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Icon(destination.icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = tint)
                        Text(stringResource(destination.label), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = tint, maxLines = 1)
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Featured.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Featured.route) {
                CatalogScreen(
                    onInstall = installViewModel::install,
                    onBrowse = { navigateToTab(Destination.Browse) },
                    featuredMode = true,
                    hazeState = hazeState,
                    liquidGlassEnabled = liquidGlassEnabled,
                    viewModel = catalogViewModel
                )
            }
            composable(Destination.Browse.route) {
                CatalogScreen(
                    onInstall = installViewModel::install,
                    hazeState = hazeState,
                    liquidGlassEnabled = liquidGlassEnabled,
                    viewModel = catalogViewModel
                )
            }
            composable(Destination.Installed.route) {
                InstalledScreen(onUpdate = installViewModel::install)
            }
        }
    }

    installState?.let { state ->
        ConsoleSheet(
            state = state,
            onDismiss = installViewModel::dismiss,
            onReboot = installViewModel::reboot,
            diagnosticHeader = stringResource(
                R.string.console_log_header,
                BuildConfig.VERSION_NAME,
                Build.VERSION.SDK_INT,
                Build.VERSION.RELEASE,
                Build.MODEL,
                rootStatus.manager?.displayName ?: stringResource(R.string.status_no_root)
            )
        )
    }

    if (showSettings) {
        val repository = (context.applicationContext as HerculesApp).container.repository
        SettingsSheet(
            hazeState = hazeState,
            glassEnabled = liquidGlassEnabled,
            onGlassChange = {
                liquidGlassEnabled = it
                preferences.edit().putBoolean("liquidGlassEnabled", it).apply()
            },
            onRefresh = {
                scope.launch { repository.refresh() }
            },
            initialCatalogUrls = repository.customCatalogUrls(),
            onCatalogUrlsChange = { urls ->
                repository.saveCustomCatalogUrls(urls)
                scope.launch { repository.refresh() }
            },
            onClearCache = { context.cacheDir.deleteRecursively() },
            onDismiss = { showSettings = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    hazeState: HazeState,
    glassEnabled: Boolean,
    onGlassChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    initialCatalogUrls: List<String>,
    onCatalogUrlsChange: (List<String>) -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var catalogUrls by rememberSaveable { mutableStateOf(initialCatalogUrls) }
    var catalogDraft by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
        Column(
            Modifier.fillMaxWidth().glassSurface(hazeState, glassEnabled, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(stringResource(R.string.settings_glass), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_glass_subtitle), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                Switch(checked = glassEnabled, onCheckedChange = onGlassChange)
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.25f), thickness = 1.dp)
            Text(stringResource(R.string.settings_catalogs_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = catalogDraft,
                onValueChange = { catalogDraft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.settings_catalog_url_hint)) }
            )
            TextButton(onClick = {
                val url = catalogDraft.trim()
                if (url.isNotBlank() && url !in catalogUrls) {
                    catalogUrls = catalogUrls + url
                    onCatalogUrlsChange(catalogUrls)
                }
                catalogDraft = ""
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_catalog_add)) }
            catalogUrls.forEach { url ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(url, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    TextButton(onClick = {
                        catalogUrls = catalogUrls - url
                        onCatalogUrlsChange(catalogUrls)
                    }) { Text(stringResource(R.string.settings_catalog_remove)) }
                }
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.25f), thickness = 1.dp)
            TextButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_refresh_catalog)) }
            TextButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_clear_cache)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("vuloid/hercules", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                Text(stringResource(R.string.settings_build_version), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

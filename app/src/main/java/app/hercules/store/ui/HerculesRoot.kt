package app.hercules.store.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.hercules.store.R
import app.hercules.store.ui.catalog.CatalogScreen
import app.hercules.store.ui.install.ConsoleSheet
import app.hercules.store.ui.install.InstallViewModel
import app.hercules.store.ui.installed.InstalledScreen

private enum class Destination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    Catalog("catalog", R.string.tab_catalog, Icons.Rounded.Apps),
    Installed("installed", R.string.tab_installed, Icons.Rounded.Inventory2)
}

@Composable
fun HerculesRoot() {
    val navController = rememberNavController()
    val installViewModel: InstallViewModel = viewModel(factory = HerculesViewModelFactory)
    val installState by installViewModel.state.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = scheme.surfaceContainer, tonalElevation = 0.dp) {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = scheme.primary,
                            selectedTextColor = scheme.primary,
                            indicatorColor = scheme.primary.copy(alpha = 0.16f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Catalog.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Catalog.route) {
                CatalogScreen(onInstall = installViewModel::install)
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
            onReboot = installViewModel::reboot
        )
    }
}

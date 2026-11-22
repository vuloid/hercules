package app.hercules.store.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.hercules.store.AppContainer
import app.hercules.store.HerculesApp
import app.hercules.store.ui.catalog.CatalogViewModel
import app.hercules.store.ui.details.DetailsViewModel
import app.hercules.store.ui.install.InstallViewModel
import app.hercules.store.ui.installed.InstalledViewModel

val HerculesViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { CatalogViewModel(container().repository) }
    initializer { DetailsViewModel(container().repository) }
    initializer { InstalledViewModel(container().repository, container().rootExecutor) }
    initializer { InstallViewModel(container().repository, container().rootExecutor) }
}

private fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as HerculesApp).container

package ca.udes.reparecampus.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.udes.reparecampus.AppContainer
import ca.udes.reparecampus.RepareCampusApp

/** Raccourci : crée un ViewModel à partir du conteneur de dépendances de l'application. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as RepareCampusApp).container
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}

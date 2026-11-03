package ca.usherbrooke.reparecampus.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.usherbrooke.reparecampus.RepareCampusApp
import ca.usherbrooke.reparecampus.ui.auth.AuthViewModel
import ca.usherbrooke.reparecampus.ui.notifications.NotificationsViewModel
import ca.usherbrooke.reparecampus.ui.profile.ProfileViewModel
import ca.usherbrooke.reparecampus.ui.requests.CreateRequestViewModel
import ca.usherbrooke.reparecampus.ui.requests.DraftsViewModel
import ca.usherbrooke.reparecampus.ui.requests.RequestDetailViewModel
import ca.usherbrooke.reparecampus.ui.requests.RequestListViewModel

// Récupère l'Application (et donc le conteneur de dépendances) depuis les CreationExtras.
fun CreationExtras.app(): RepareCampusApp = this[APPLICATION_KEY] as RepareCampusApp

// Fabrique unique de ViewModels : injection manuelle via le conteneur de l'application.
// Pas de bibliothèque de DI — architecture explicite (grille 2.6).
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { AuthViewModel(app().container) }
        initializer { RequestListViewModel(app().container) }
        initializer { CreateRequestViewModel(app().container) }
        initializer { DraftsViewModel(app().container) }
        initializer { NotificationsViewModel(app().container) }
        initializer { ProfileViewModel(app().container) }
        // Le détail lit l'identifiant de la demande via l'argument de navigation (SavedStateHandle).
        initializer { RequestDetailViewModel(app().container, createSavedStateHandle()) }
    }
}

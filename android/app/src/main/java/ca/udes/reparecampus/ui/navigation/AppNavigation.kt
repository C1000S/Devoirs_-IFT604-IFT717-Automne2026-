package ca.udes.reparecampus.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ca.udes.reparecampus.data.session.SessionState
import ca.udes.reparecampus.domain.Session
import ca.udes.reparecampus.ui.appViewModel
import ca.udes.reparecampus.ui.auth.AuthViewModel
import ca.udes.reparecampus.ui.auth.LoginScreen
import ca.udes.reparecampus.ui.auth.RegisterScreen
import ca.udes.reparecampus.ui.create.CreateRequestScreen
import ca.udes.reparecampus.ui.create.CreateRequestViewModel
import ca.udes.reparecampus.ui.detail.RequestDetailScreen
import ca.udes.reparecampus.ui.detail.RequestDetailViewModel
import ca.udes.reparecampus.ui.requests.RequestListScreen
import ca.udes.reparecampus.ui.requests.RequestListViewModel
import kotlinx.serialization.Serializable

@Serializable private data object LoginRoute
@Serializable private data object RegisterRoute
@Serializable private data object RequestListRoute
@Serializable data class RequestDetailRoute(val id: String)
/** draftId = 0 : nouvelle demande ; sinon reprise d'un brouillon. */
@Serializable data class CreateRequestRoute(val draftId: Long = 0L)

/**
 * Racine de navigation : bascule entre le graphe d'authentification et le graphe
 * connecté selon l'état de session. Une déconnexion (manuelle ou jeton expiré)
 * ramène donc automatiquement à l'écran de connexion.
 */
@Composable
fun AppRoot(sessionState: SessionState, deepLinkRequestId: String? = null, onDeepLinkHandled: () -> Unit = {}) {
    when (sessionState) {
        SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        SessionState.LoggedOut -> AuthNavHost()
        is SessionState.LoggedIn -> MainNavHost(sessionState.session, deepLinkRequestId, onDeepLinkHandled)
    }
}

/** Android 13+ : la permission d'afficher des notifications est demandée une fois connecté. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun AuthNavHost() {
    val navController = rememberNavController()
    // Un seul ViewModel partagé : le courriel saisi est conservé entre connexion et inscription.
    val authViewModel = appViewModel { AuthViewModel(it.authRepository) }
    NavHost(navController, startDestination = LoginRoute) {
        composable<LoginRoute> {
            LoginScreen(authViewModel, onGoToRegister = { navController.navigate(RegisterRoute) })
        }
        composable<RegisterRoute> {
            RegisterScreen(authViewModel, onGoToLogin = { navController.popBackStack() })
        }
    }
}

@Composable
private fun MainNavHost(session: Session, deepLinkRequestId: String?, onDeepLinkHandled: () -> Unit) {
    val navController = rememberNavController()
    RequestNotificationPermission()
    // Notification système touchée : ouvrir la demande concernée.
    LaunchedEffect(deepLinkRequestId) {
        deepLinkRequestId?.let {
            navController.navigate(RequestDetailRoute(it)) { launchSingleTop = true }
            onDeepLinkHandled()
        }
    }
    NavHost(navController, startDestination = RequestListRoute) {
        composable<RequestListRoute> {
            RequestListScreen(
                session = session,
                viewModel = appViewModel { RequestListViewModel(session, it.requestRepository, it.authRepository, it.draftRepository, it.networkMonitor) },
                onOpenRequest = { navController.navigate(RequestDetailRoute(it)) },
                onCreateRequest = { navController.navigate(CreateRequestRoute(it)) },
            )
        }
        composable<CreateRequestRoute> { entry ->
            val draftId = entry.toRoute<CreateRequestRoute>().draftId.takeIf { it != 0L }
            CreateRequestScreen(
                viewModel = appViewModel { CreateRequestViewModel(session, draftId, it.requestRepository, it.draftRepository) },
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.navigate(RequestDetailRoute(id)) {
                        popUpTo<CreateRequestRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<RequestDetailRoute> { entry ->
            val id = entry.toRoute<RequestDetailRoute>().id
            RequestDetailScreen(
                viewModel = appViewModel { RequestDetailViewModel(id, session, it.requestRepository, it.networkMonitor) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

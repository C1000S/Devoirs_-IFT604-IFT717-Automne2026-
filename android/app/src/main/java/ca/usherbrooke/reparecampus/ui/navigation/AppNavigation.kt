package ca.usherbrooke.reparecampus.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ca.usherbrooke.reparecampus.RepareCampusApp
import ca.usherbrooke.reparecampus.data.AppContainer
import ca.usherbrooke.reparecampus.ui.auth.LoginScreen
import ca.usherbrooke.reparecampus.ui.notifications.NotificationsScreen
import ca.usherbrooke.reparecampus.ui.profile.ProfileScreen
import ca.usherbrooke.reparecampus.ui.requests.CreateRequestScreen
import ca.usherbrooke.reparecampus.ui.requests.DraftsScreen
import ca.usherbrooke.reparecampus.ui.requests.RequestDetailScreen
import ca.usherbrooke.reparecampus.ui.requests.RequestListScreen

// Destinations de l'application.
object Routes {
    const val REQUESTS = "requests"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val CREATE = "create"
    const val DRAFTS = "drafts"
    const val DETAIL = "request/{requestId}"
    fun detail(id: String) = "request/$id"
}

// Éléments de la barre de navigation inférieure (communs à tous les rôles).
private data class NavItem(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun rememberContainer(): AppContainer {
    val app = LocalContext.current.applicationContext as RepareCampusApp
    return app.container
}

// Point d'entrée : affiche la connexion ou l'application selon la session persistée.
@Composable
fun AppRoot() {
    val container = rememberContainer()
    val session by container.authRepository.sessionFlow.collectAsStateWithLifecycle(initialValue = null)

    if (session == null) {
        LoginScreen()
    } else {
        MainScaffold()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val items = listOf(
        NavItem(Routes.REQUESTS, "Demandes") { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
        NavItem(Routes.NOTIFICATIONS, "Notifs") { Icon(Icons.Filled.Notifications, contentDescription = null) },
        NavItem(Routes.PROFILE, "Profil") { Icon(Icons.Filled.Person, contentDescription = null) }
    )

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showChrome = currentRoute in items.map { it.route }

    Scaffold(
        bottomBar = {
            if (showChrome) {
                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            selected = backStack?.destination?.hierarchy?.any { it.route == item.route } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = item.icon,
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Routes.REQUESTS) {
                FloatingActionButton(onClick = { navController.navigate(Routes.CREATE) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Nouvelle demande")
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.REQUESTS,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(Routes.REQUESTS) {
                RequestListScreen(onOpenRequest = { navController.navigate(Routes.detail(it)) })
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(onOpenRequest = { navController.navigate(Routes.detail(it)) })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(onOpenDrafts = { navController.navigate(Routes.DRAFTS) })
            }
            composable(Routes.CREATE) {
                CreateRequestScreen(onDone = { navController.popBackStack() })
            }
            composable(Routes.DRAFTS) {
                DraftsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.DETAIL) {
                RequestDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

package ca.usherbrooke.reparecampus

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ca.usherbrooke.reparecampus.ui.navigation.AppRoot
import ca.usherbrooke.reparecampus.ui.theme.RepareCampusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RepareCampusTheme {
                // Demande la permission de notifications une seule fois au lancement (API 33+).
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* le résultat n'empêche pas l'utilisation de l'application */ }

                var requested by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    if (!requested && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        requested = true
                    }
                }

                AppRoot()
            }
        }
    }
}

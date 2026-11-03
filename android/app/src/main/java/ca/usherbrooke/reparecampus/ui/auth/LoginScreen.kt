package ca.usherbrooke.reparecampus.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.usherbrooke.reparecampus.ui.AppViewModelProvider
import ca.usherbrooke.reparecampus.util.Constants
import ca.usherbrooke.reparecampus.util.Labels

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isRegister = state.mode == AuthMode.REGISTER

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "RépareCampus",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            if (isRegister) "Créer un compte" else "Connexion",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(24.dp))

        OutlinedTextField(
            value = state.serverUrl,
            onValueChange = viewModel::onServerUrl,
            label = { Text("URL du serveur") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )
        Spacer(Modifier.size(12.dp))

        if (isRegister) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text("Nom complet") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(12.dp))
            RoleDropdown(selected = state.role, onSelect = viewModel::onRole)
            Spacer(Modifier.size(12.dp))
        }

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmail,
            label = { Text("Courriel") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.size(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPassword,
            label = { Text("Mot de passe") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        if (state.error != null) {
            Spacer(Modifier.size(12.dp))
            Text(
                state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.size(20.dp))
        Button(
            onClick = viewModel::submit,
            enabled = state.canSubmit && !state.loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(if (isRegister) "S'inscrire" else "Se connecter")
            }
        }

        Spacer(Modifier.size(8.dp))
        TextButton(
            onClick = viewModel::toggleMode,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (isRegister) "J'ai déjà un compte" else "Créer un compte")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val roles = listOf(
        Constants.ROLE_REQUESTER,
        Constants.ROLE_REPAIRER,
        Constants.ROLE_COORDINATOR,
        Constants.ROLE_ADMIN
    )
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = Labels.role(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Rôle") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            roles.forEach { role ->
                DropdownMenuItem(
                    text = { Text(Labels.role(role)) },
                    onClick = { onSelect(role); expanded = false }
                )
            }
        }
    }
}

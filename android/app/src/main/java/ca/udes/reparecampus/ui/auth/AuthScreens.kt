package ca.udes.reparecampus.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.udes.reparecampus.BuildConfig

@Composable
fun LoginScreen(viewModel: AuthViewModel, onGoToRegister: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScaffold(subtitle = "Connexion") {
        EmailField(state.email, viewModel::onEmailChange, enabled = !state.isLoading)
        PasswordField(state.password, viewModel::onPasswordChange, enabled = !state.isLoading, onDone = viewModel::login)
        ErrorText(state.error)
        SubmitButton("Se connecter", state.isLoading, viewModel::login)
        TextButton(onClick = onGoToRegister, enabled = !state.isLoading) {
            Text("Pas encore de compte ? S'inscrire")
        }
    }
}

@Composable
fun RegisterScreen(viewModel: AuthViewModel, onGoToLogin: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScaffold(subtitle = "Créer un compte demandeur") {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::onNameChange,
            label = { Text("Nom complet") },
            singleLine = true,
            enabled = !state.isLoading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        EmailField(state.email, viewModel::onEmailChange, enabled = !state.isLoading)
        PasswordField(state.password, viewModel::onPasswordChange, enabled = !state.isLoading, onDone = viewModel::register)
        Text(
            "Les comptes réparateur, coordination et admin sont créés par l'administration.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ErrorText(state.error)
        SubmitButton("S'inscrire", state.isLoading, viewModel::register)
        TextButton(onClick = onGoToLogin, enabled = !state.isLoading) {
            Text("Déjà un compte ? Se connecter")
        }
    }
}

@Composable
private fun AuthScaffold(subtitle: String, content: @Composable () -> Unit) {
    Scaffold { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.Build,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
                Text("RépareCampus", style = MaterialTheme.typography.headlineMedium)
                Text(subtitle, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
                Text(
                    "API : ${BuildConfig.API_BASE_URL}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Courriel") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, enabled: Boolean, onDone: () -> Unit) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Mot de passe") },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Masquer le mot de passe" else "Afficher le mot de passe",
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ErrorText(error: String?) {
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SubmitButton(label: String, loading: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(label)
        }
    }
}

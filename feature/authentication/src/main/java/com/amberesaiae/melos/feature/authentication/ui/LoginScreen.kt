@file:Suppress("kotlin:S6290", "kotlin:S100", "kotlin:S3776")

package com.amberesaiae.melos.feature.authentication.ui

import androidx.activity.ComponentActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Server
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amberesaiae.melos.feature.authentication.domain.LoginEvent
import com.amberesaiae.melos.feature.authentication.domain.LoginViewModel
import com.amberesaiae.melos.feature.authentication.domain.LoginUiState
import com.amberesaiae.melos.feature.authentication.domain.ServerType

/**
 * Login screen composable with username/password inputs, server selection, and biometric auth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (username: String, serverType: ServerType) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val loginEvent by viewModel.loginEvent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle login events
    LaunchedEffect(loginEvent) {
        when (val event = loginEvent) {
            is LoginEvent.LoginSuccess -> {
                onLoginSuccess(event.username, event.serverType)
            }
            is LoginEvent.LoginFailed -> {
                snackbarHostState.showSnackbar(event.error)
            }
            is LoginEvent.Logout -> {
                snackbarHostState.showSnackbar("Logged out successfully")
            }
            null -> {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center
        ) {
            LoginContent(
                uiState = uiState,
                onUsernameChange = viewModel::onUsernameChange,
                onPasswordChange = viewModel::onPasswordChange,
                onServerUrlChange = viewModel::onServerUrlChange,
                onServerTypeChange = viewModel::onServerTypeChange,
                onAutoLoginChange = viewModel::onAutoLoginChange,
                onBiometricToggle = viewModel::toggleBiometricAuth,
                onLoginClick = viewModel::login,
                onBiometricLogin = {
                    // Launch biometric authentication
                    val activity = context as? ComponentActivity
                    if (activity != null) {
                        viewModel.authenticateWithBiometrics()
                    }
                },
                onLogoutClick = viewModel::logout
            )
        }
    }
}

/**
 * Main login content composable.
 */
@Composable
private fun LoginContent(
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onServerTypeChange: (ServerType) -> Unit,
    onAutoLoginChange: (Boolean) -> Unit,
    onBiometricToggle: () -> Unit,
    onLoginClick: () -> Unit,
    onBiometricLogin: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Welcome to Melos",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sign in to your music server",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Biometric login button (if available)
            if (uiState.showBiometricPrompt && uiState.hasStoredCredentials) {
                BiometricLoginButton(
                    onClick = onBiometricLogin,
                    isLoading = uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "or sign in with credentials",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Username field
            UsernameField(
                username = uiState.username,
                onUsernameChange = onUsernameChange,
                error = uiState.usernameError,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Password field
            PasswordField(
                password = uiState.password,
                onPasswordChange = onPasswordChange,
                error = uiState.passwordError,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Server type dropdown
            ServerTypeDropdown(
                serverType = uiState.serverType,
                onServerTypeChange = onServerTypeChange,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Server URL field
            ServerUrlField(
                serverUrl = uiState.serverUrl,
                onServerUrlChange = onServerUrlChange,
                error = uiState.serverUrlError,
                isCheckingServer = uiState.isCheckingServer,
                isServerAvailable = uiState.isServerAvailable,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Auto-login toggle
            AutoLoginToggle(
                enabled = uiState.autoLoginEnabled,
                onEnabledChange = onAutoLoginChange,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Biometric toggle
            if (uiState.biometricEnabled || canEnableBiometric()) {
                BiometricToggle(
                    enabled = uiState.biometricEnabled,
                    onToggle = onBiometricToggle,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Login button
            LoginButton(
                onClick = onLoginClick,
                enabled = uiState.isLoginButtonEnabled && !uiState.isLoading,
                isLoading = uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            // Error message
            uiState.loginError?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            // Logout button (if logged in)
            if (uiState.isLoginSuccessful) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onLogoutClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    Text("Logout")
                }
            }
        }
    }
}

/**
 * Username input field.
 */
@Composable
private fun UsernameField(
    username: String,
    onUsernameChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("Username") },
        leadingIcon = {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        enabled = enabled,
        singleLine = true,
        modifier = modifier
    )
}

/**
 * Password input field with visibility toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password") },
        leadingIcon = {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            val icon = if (passwordVisible) {
                Icons.Default.Visibility
            } else {
                Icons.Default.VisibilityOff
            }
            val description = if (passwordVisible) "Hide password" else "Show password"
            androidx.compose.material3.IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(icon, contentDescription = description)
            }
        },
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        enabled = enabled,
        singleLine = true,
        modifier = modifier
    )
}

/**
 * Server type selection dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerTypeDropdown(
    serverType: ServerType,
    onServerTypeChange: (ServerType) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = serverType.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Server Type") },
            leadingIcon = {
                Icon(
                    Icons.Default.Server,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            enabled = enabled,
            modifier = modifier.menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ServerType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.displayName) },
                    onClick = {
                        onServerTypeChange(type)
                        expanded = false
                    },
                    leadingIcon = {
                        if (type == serverType) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * Server URL input field with connection status indicator.
 */
@Composable
private fun ServerUrlField(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    error: String?,
    isCheckingServer: Boolean,
    isServerAvailable: Boolean?,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = serverUrl,
        onValueChange = onServerUrlChange,
        label = { Text("Server URL") },
        placeholder = { Text("http://localhost:4040") },
        leadingIcon = {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = when (isServerAvailable) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        trailingIcon = {
            if (isCheckingServer) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else if (isServerAvailable == true) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Server available",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        enabled = enabled,
        singleLine = true,
        modifier = modifier
    )
}

/**
 * Auto-login toggle switch.
 */
@Composable
private fun AutoLoginToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Auto-login",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Automatically sign in on app launch",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange
        )
    }
}

/**
 * Biometric authentication toggle switch.
 */
@Composable
private fun BiometricToggle(
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Biometric Authentication",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Use fingerprint or face to sign in",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = { onToggle() }
        )
    }
}

/**
 * Biometric login button.
 */
@Composable
private fun BiometricLoginButton(
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Biotech,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in with Biometrics")
            }
        }
    }
}

/**
 * Primary login button.
 */
@Composable
private fun LoginButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Login,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign In")
            }
        }
    }
}

/**
 * Check if biometric authentication can be enabled.
 */
@Composable
private fun canEnableBiometric(): Boolean {
    val context = LocalContext.current
    val biometricManager = androidx.biometric.BiometricManager.from(context)
    return when (biometricManager.canAuthenticate(
        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
    )) {
        androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> true
        else -> false
    }
}

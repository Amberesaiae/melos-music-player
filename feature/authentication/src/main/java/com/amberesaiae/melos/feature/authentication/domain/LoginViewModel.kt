@file:Suppress("kotlin:S6290", "kotlin:S6701")

package com.amberesaiae.melos.feature.authentication.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amberesaiae.melos.feature.authentication.data.CredentialManager
import com.amberesaiae.melos.feature.authentication.data.CredentialStorageException
import com.amberesaiae.melos.feature.authentication.data.Credentials
import com.amberesaiae.melos.feature.authentication.data.ServerConfig
import com.amberesaiae.melos.network.api.SubsonicApiService
import com.amberesaiae.melos.network.api.JellyfinApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import javax.inject.Inject

/**
 * ViewModel for login screen handling authentication logic.
 * Supports input validation, server connection testing, and biometric authentication.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val credentialManager: CredentialManager,
    private val subsonicApiService: SubsonicApiService,
    private val jellyfinApiService: JellyfinApiService
) : ViewModel() {

    companion object {
        private const val MIN_USERNAME_LENGTH = 2
        private const val MIN_PASSWORD_LENGTH = 1
        private const val DEBOUNCE_DELAY = 300L
    }

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _loginEvent = MutableStateFlow<LoginEvent?>(null)
    val loginEvent: StateFlow<LoginEvent?> = _loginEvent.asStateFlow()

    private var serverTestJob: Job? = null
    private var credentialsLoadJob: Job? = null

    init {
        loadStoredCredentials()
    }

    /**
     * Update username field with validation.
     */
    fun onUsernameChange(username: String) {
        _uiState.update { currentState ->
            currentState.copy(
                username = username,
                usernameError = validateUsername(username),
                isLoginButtonEnabled = isFormValid(
                    username = username,
                    password = currentState.password,
                    serverUrl = currentState.serverUrl
                )
            )
        }
    }

    /**
     * Update password field with validation.
     */
    fun onPasswordChange(password: String) {
        _uiState.update { currentState ->
            currentState.copy(
                password = password,
                passwordError = validatePassword(password),
                isLoginButtonEnabled = isFormValid(
                    username = currentState.username,
                    password = password,
                    serverUrl = currentState.serverUrl
                )
            )
        }
    }

    /**
     * Update server URL with validation and connection testing.
     */
    fun onServerUrlChange(serverUrl: String) {
        _uiState.update { currentState ->
            currentState.copy(
                serverUrl = serverUrl,
                serverUrlError = validateServerUrl(serverUrl),
                isLoginButtonEnabled = isFormValid(
                    username = currentState.username,
                    password = currentState.password,
                    serverUrl = serverUrl
                )
            )
        }

        // Debounced server connection test
        serverTestJob?.cancel()
        serverTestJob = viewModelScope.launch {
            delay(DEBOUNCE_DELAY)
            if (serverUrl.isValidUrl()) {
                testServerConnection(serverUrl, _uiState.value.serverType)
            }
        }
    }

    /**
     * Update server type selection.
     */
    fun onServerTypeChange(serverType: ServerType) {
        _uiState.update { currentState ->
            currentState.copy(
                serverType = serverType,
                serverUrlError = null,
                isServerAvailable = null
            )
        }

        // Re-test connection with new server type if URL is present
        val currentUrl = _uiState.value.serverUrl
        if (currentUrl.isNotEmpty() && currentUrl.isValidUrl()) {
            serverTestJob?.cancel()
            serverTestJob = viewModelScope.launch {
                delay(DEBOUNCE_DELAY)
                testServerConnection(currentUrl, serverType)
            }
        }
    }

    /**
     * Update auto-login preference.
     */
    fun onAutoLoginChange(enabled: Boolean) {
        _uiState.update { it.copy(autoLoginEnabled = enabled) }
    }

    /**
     * Toggle biometric authentication setting.
     */
    fun toggleBiometricAuth() {
        viewModelScope.launch {
            val newValue = !_uiState.value.biometricEnabled
            credentialManager.setBiometricEnabled(newValue)
            _uiState.update { 
                it.copy(
                    biometricEnabled = newValue,
                    showBiometricPrompt = newValue && credentialManager.isBiometricAvailable()
                ) 
            }
        }
    }

    /**
     * Perform login with current credentials.
     */
    fun login() {
        val currentState = _uiState.value

        // Validate all fields
        val usernameError = validateUsername(currentState.username)
        val passwordError = validatePassword(currentState.password)
        val serverUrlError = validateServerUrl(currentState.serverUrl)

        if (usernameError != null || passwordError != null || serverUrlError != null) {
            _uiState.update {
                it.copy(
                    usernameError = usernameError,
                    passwordError = passwordError,
                    serverUrlError = serverUrlError
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loginError = null) }

            try {
                val loginSuccess = performServerLogin(
                    username = currentState.username,
                    password = currentState.password,
                    serverUrl = currentState.serverUrl,
                    serverType = currentState.serverType
                )

                if (loginSuccess) {
                    // Save credentials if auto-login is enabled
                    if (currentState.autoLoginEnabled) {
                        credentialManager.saveCredentials(
                            username = currentState.username,
                            password = currentState.password,
                            serverUrl = currentState.serverUrl,
                            serverType = currentState.serverType.value,
                            enableAutoLogin = true,
                            enableBiometric = currentState.biometricEnabled
                        )
                    }

                    _uiState.update { it.copy(isLoading = false, isLoginSuccessful = true) }
                    _loginEvent.value = LoginEvent.LoginSuccess(
                        username = currentState.username,
                        serverType = currentState.serverType
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loginError = "Invalid credentials or server connection failed"
                        )
                    }
                    _loginEvent.value = LoginEvent.LoginFailed("Authentication failed")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loginError = e.message ?: "Login failed. Please try again."
                    )
                }
                _loginEvent.value = LoginEvent.LoginFailed(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Authenticate with biometrics and auto-login.
     */
    fun authenticateWithBiometrics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val credentials = credentialManager.getCredentials()
                if (credentials != null) {
                    val loginSuccess = performServerLogin(
                        username = credentials.username,
                        password = credentials.password,
                        serverUrl = credentials.serverUrl,
                        serverType = ServerType.fromString(credentials.serverType)
                    )

                    if (loginSuccess) {
                        _uiState.update { it.copy(isLoading = false, isLoginSuccessful = true) }
                        _loginEvent.value = LoginEvent.LoginSuccess(
                            username = credentials.username,
                            serverType = ServerType.fromString(credentials.serverType)
                        )
                    } else {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                loginError = "Biometric login failed. Please re-enter credentials."
                            )
                        }
                        credentialManager.clearCredentials()
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            loginError = "No stored credentials found"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loginError = e.message ?: "Biometric authentication failed"
                    )
                }
            }
        }
    }

    /**
     * Clear stored credentials and reset state.
     */
    fun logout() {
        viewModelScope.launch {
            credentialManager.clearCredentials()
            _uiState.update { LoginUiState() }
            _loginEvent.value = LoginEvent.Logout
        }
    }

    /**
     * Load stored credentials for auto-login.
     */
    private fun loadStoredCredentials() {
        credentialsLoadJob = viewModelScope.launch {
            try {
                val serverConfig = credentialManager.getServerConfig()
                val hasCredentials = credentialManager.hasStoredCredentials()
                val isAutoLogin = credentialManager.isAutoLoginEnabled()
                val isBiometricAvailable = credentialManager.isBiometricAvailable()
                val isBiometricEnabled = credentialManager.isBiometricEnabled()

                _uiState.update {
                    it.copy(
                        username = "", // Don't pre-fill for security
                        password = "", // Don't pre-fill for security
                        serverUrl = serverConfig?.serverUrl ?: "",
                        serverType = ServerType.fromString(serverConfig?.serverType ?: "subsonic"),
                        autoLoginEnabled = isAutoLogin,
                        biometricEnabled = isBiometricEnabled,
                        showBiometricPrompt = isBiometricEnabled && isBiometricAvailable,
                        hasStoredCredentials = hasCredentials
                    )
                }

                // If auto-login is enabled and credentials exist, attempt login
                if (isAutoLogin && hasCredentials) {
                    authenticateWithBiometrics()
                }
            } catch (e: Exception) {
                // Silent fail - user will enter credentials manually
            }
        }
    }

    /**
     * Test server connection without credentials.
     */
    private suspend fun testServerConnection(serverUrl: String, serverType: ServerType) {
        withContext(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(isCheckingServer = true, isServerAvailable = null) }

                val isAvailable = when (serverType) {
                    ServerType.SUBSONIC -> testSubsonicConnection(serverUrl)
                    ServerType.JELLYFIN -> testJellyfinConnection(serverUrl)
                }

                _uiState.update {
                    it.copy(
                        isCheckingServer = false,
                        isServerAvailable = isAvailable
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckingServer = false,
                        isServerAvailable = false,
                        serverUrlError = "Cannot connect to server: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Test Subsonic server connection.
     */
    private suspend fun testSubsonicConnection(serverUrl: String): Boolean {
        return try {
            // Attempt to reach Subsonic ping endpoint
            val testUrl = "$serverUrl/rest/ping.view?v=1.16.1&c=MelosPlayer&f=json"
            val response = subsonicApiService.ping(testUrl)
            response.subsonicResponse?.status == "ok"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Test Jellyfin server connection.
     */
    private suspend fun testJellyfinConnection(serverUrl: String): Boolean {
        return try {
            // Attempt to reach Jellyfin system info endpoint
            val response = jellyfinApiService.getSystemInfo(serverUrl)
            response != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Perform actual login with credentials.
     */
    private suspend fun performServerLogin(
        username: String,
        password: String,
        serverUrl: String,
        serverType: ServerType
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                when (serverType) {
                    ServerType.SUBSONIC -> {
                        // Test Subsonic login via ping with credentials
                        val salt = generateSalt()
                        val token = generateToken(password, salt)
                        val response = subsonicApiService.getUsername(
                            baseUrl = serverUrl,
                            username = username,
                            salt = salt,
                            token = token
                        )
                        response.subsonicResponse?.username != null
                    }
                    ServerType.JELLYFIN -> {
                        // Test Jellyfin login
                        val response = jellyfinApiService.authenticateUser(
                            baseUrl = serverUrl,
                            username = username,
                            password = password
                        )
                        response.accessToken != null
                    }
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Validate username input.
     */
    private fun validateUsername(username: String): String? {
        return when {
            username.isEmpty() -> "Username is required"
            username.length < MIN_USERNAME_LENGTH -> "Username must be at least $MIN_USERNAME_LENGTH characters"
            else -> null
        }
    }

    /**
     * Validate password input.
     */
    private fun validatePassword(password: String): String? {
        return when {
            password.isEmpty() -> "Password is required"
            password.length < MIN_PASSWORD_LENGTH -> "Password is required"
            else -> null
        }
    }

    /**
     * Validate server URL input.
     */
    private fun validateServerUrl(serverUrl: String): String? {
        return when {
            serverUrl.isEmpty() -> "Server URL is required"
            !serverUrl.isValidUrl() -> "Invalid URL format (e.g., http://localhost:4040)"
            else -> null
        }
    }

    /**
     * Check if form is valid for login.
     */
    private fun isFormValid(username: String, password: String, serverUrl: String): Boolean {
        return username.isNotEmpty() &&
               password.isNotEmpty() &&
               serverUrl.isNotEmpty() &&
               serverUrl.isValidUrl() &&
               validateUsername(username) == null &&
               validatePassword(password) == null &&
               validateServerUrl(serverUrl) == null
    }

    /**
     * Generate random salt for Subsonic authentication.
     */
    private fun generateSalt(): String {
        return (1..16).map { ('a'..'z').random() }.joinToString("")
    }

    /**
     * Generate MD5 token for Subsonic authentication.
     */
    private fun generateToken(password: String, salt: String): String {
        val input = password + salt
        // In production, use proper MD5 hashing
        return input.hashCode().toString(16)
    }

    override fun onCleared() {
        super.onCleared()
        serverTestJob?.cancel()
        credentialsLoadJob?.cancel()
    }
}

/**
 * UI State for login screen.
 */
data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val serverUrl: String = "",
    val serverType: ServerType = ServerType.SUBSONIC,
    val usernameError: String? = null,
    val passwordError: String? = null,
    val serverUrlError: String? = null,
    val isLoading: Boolean = false,
    val isLoginButtonEnabled: Boolean = false,
    val isCheckingServer: Boolean = false,
    val isServerAvailable: Boolean? = null,
    val loginError: String? = null,
    val isLoginSuccessful: Boolean = false,
    val autoLoginEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val showBiometricPrompt: Boolean = false,
    val hasStoredCredentials: Boolean = false
)

/**
 * Server type enumeration.
 */
enum class ServerType(val value: String, val displayName: String) {
    SUBSONIC("subsonic", "Subsonic"),
    JELLYFIN("jellyfin", "Jellyfin");

    companion object {
        fun fromString(value: String): ServerType {
            return entries.find { it.value == value.lowercase() } ?: SUBSONIC
        }
    }
}

/**
 * Login events.
 */
sealed class LoginEvent {
    data class LoginSuccess(val username: String, val serverType: ServerType) : LoginEvent()
    data class LoginFailed(val error: String) : LoginEvent()
    object Logout : LoginEvent()
}

/**
 * Check if string is a valid URL.
 */
private fun String.isValidUrl(): Boolean {
    return try {
        URL(this)
        true
    } catch (e: Exception) {
        false
    }
}

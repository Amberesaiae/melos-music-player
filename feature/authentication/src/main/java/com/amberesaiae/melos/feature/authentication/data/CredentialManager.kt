@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.feature.authentication.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.KeyStore
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages secure storage and retrieval of user credentials.
 * Uses EncryptedSharedPreferences with Android Keystore for encryption.
 * Supports biometric authentication for credential access.
 */
@Singleton
class CredentialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "secure_credentials"
        private const val KEY_USERNAME = "encrypted_username"
        private const val KEY_PASSWORD = "encrypted_password"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_TYPE = "server_type"
        private const val KEY_AUTO_LOGIN = "auto_login_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "melos_master_key"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private val _biometricState = MutableStateFlow(BiometricState())
    val biometricState: StateFlow<BiometricState> = _biometricState.asStateFlow()

    private val executor: Executor by lazy {
        ContextCompat.getMainExecutor(context)
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Check if biometric authentication is available and enrolled.
     */
    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * Check if credentials are stored.
     */
    fun hasStoredCredentials(): Boolean {
        return encryptedPrefs.getString(KEY_USERNAME, null) != null &&
               encryptedPrefs.getString(KEY_PASSWORD, null) != null
    }

    /**
     * Check if auto-login is enabled.
     */
    fun isAutoLoginEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_AUTO_LOGIN, false)
    }

    /**
     * Check if biometric authentication is enabled.
     */
    fun isBiometricEnabled(): Boolean {
        return encryptedPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false) && isBiometricAvailable()
    }

    /**
     * Save user credentials securely.
     */
    suspend fun saveCredentials(
        username: String,
        password: String,
        serverUrl: String,
        serverType: String,
        enableAutoLogin: Boolean = true,
        enableBiometric: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .putString(KEY_SERVER_URL, serverUrl)
                .putString(KEY_SERVER_TYPE, serverType)
                .putBoolean(KEY_AUTO_LOGIN, enableAutoLogin)
                .putBoolean(KEY_BIOMETRIC_ENABLED, enableBiometric && isBiometricAvailable())
                .apply()
            
            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(CredentialStorageException("Failed to save credentials: ${e.message}", e))
        } catch (e: SecurityException) {
            Result.failure(CredentialStorageException("Security error while saving: ${e.message}", e))
        }
    }

    /**
     * Retrieve stored credentials.
     * Returns null if no credentials are stored.
     */
    suspend fun getCredentials(): Credentials? = withContext(Dispatchers.IO) {
        val username = encryptedPrefs.getString(KEY_USERNAME, null)
        val password = encryptedPrefs.getString(KEY_PASSWORD, null)
        val serverUrl = encryptedPrefs.getString(KEY_SERVER_URL, null)
        val serverType = encryptedPrefs.getString(KEY_SERVER_TYPE, null)

        if (username != null && password != null && serverUrl != null && serverType != null) {
            Credentials(username, password, serverUrl, serverType)
        } else {
            null
        }
    }

    /**
     * Get server configuration without credentials (for connection testing).
     */
    suspend fun getServerConfig(): ServerConfig? = withContext(Dispatchers.IO) {
        val serverUrl = encryptedPrefs.getString(KEY_SERVER_URL, null)
        val serverType = encryptedPrefs.getString(KEY_SERVER_TYPE, null)

        if (serverUrl != null && serverType != null) {
            ServerConfig(serverUrl, serverType)
        } else {
            null
        }
    }

    /**
     * Clear all stored credentials.
     */
    suspend fun clearCredentials(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit()
                .remove(KEY_USERNAME)
                .remove(KEY_PASSWORD)
                .remove(KEY_SERVER_URL)
                .remove(KEY_SERVER_TYPE)
                .remove(KEY_AUTO_LOGIN)
                .remove(KEY_BIOMETRIC_ENABLED)
                .apply()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(CredentialStorageException("Failed to clear credentials: ${e.message}", e))
        }
    }

    /**
     * Authenticate with biometrics before accessing credentials.
     */
    fun authenticateWithBiometrics(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: (Int) -> Unit
    ): BiometricPrompt {
        _biometricState.value = _biometricState.value.copy(isAuthenticating = true)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate to Access Credentials")
            .setSubtitle("Use your biometric credential to securely access your saved login")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                    BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        val biometricPrompt = BiometricPrompt(
            androidx.activity.ComponentActivity::class.java.getDeclaredConstructor().newInstance().apply {
                // This is a simplified example - in production, pass the actual Activity
            },
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    _biometricState.value = _biometricState.value.copy(
                        isAuthenticating = false,
                        lastAuthSuccess = true
                    )
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    _biometricState.value = _biometricState.value.copy(
                        isAuthenticating = false,
                        lastAuthSuccess = false
                    )
                    onError(errString.toString())
                    onFailed(errorCode)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed(BiometricPrompt.ERROR_NEGATIVE_BUTTON)
                }
            }
        )

        biometricPrompt.authenticate(promptInfo)
        return biometricPrompt
    }

    /**
     * Update auto-login setting.
     */
    suspend fun setAutoLogin(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs.edit()
                .putBoolean(KEY_AUTO_LOGIN, enabled)
                .apply()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(CredentialStorageException("Failed to update auto-login: ${e.message}", e))
        }
    }

    /**
     * Update biometric authentication setting.
     */
    suspend fun setBiometricEnabled(enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val canEnable = !enabled || isBiometricAvailable()
            if (canEnable) {
                encryptedPrefs.edit()
                    .putBoolean(KEY_BIOMETRIC_ENABLED, enabled)
                    .apply()
                Result.success(Unit)
            } else {
                Result.failure(CredentialStorageException("Biometric authentication not available"))
            }
        } catch (e: Exception) {
            Result.failure(CredentialStorageException("Failed to update biometric setting: ${e.message}", e))
        }
    }
}

/**
 * Data class representing stored credentials.
 */
data class Credentials(
    val username: String,
    val password: String,
    val serverUrl: String,
    val serverType: String
)

/**
 * Data class representing server configuration.
 */
data class ServerConfig(
    val serverUrl: String,
    val serverType: String
)

/**
 * State for biometric authentication.
 */
data class BiometricState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val isAuthenticating: Boolean = false,
    val lastAuthSuccess: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Exception for credential storage errors.
 */
class CredentialStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)

@file:Suppress("ktlint:standard:max-line-length")

package com.amberesaiae.melos.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.amberesaiae.melos.settings.R
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerDialog(
    initialServer: ServerConfig? = null,
    onDismiss: () -> Unit,
    onSave: (ServerConfig) -> Unit
) {
    var serverName by remember { mutableStateOf(initialServer?.name ?: "") }
    var serverUrl by remember { mutableStateOf(initialServer?.baseUrl ?: "") }
    var serverType by remember { mutableStateOf(initialServer?.serverType ?: ServerType.SUBSONIC) }
    var username by remember { mutableStateOf(initialServer?.username ?: "") }
    var password by remember { mutableStateOf("") }
    
    var expanded by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<Boolean?>(null) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isValid = validateServerData(serverName, serverUrl, username, password, isEditing = initialServer != null)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = {
            Text(
                text = if (initialServer == null) 
                    stringResource(R.string.add_server) 
                    else stringResource(R.string.edit_server_title)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text(stringResource(R.string.server_name)) },
                    placeholder = { Text(stringResource(R.string.server_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = serverName.isBlank()
                )
                
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { 
                        serverUrl = it
                        connectionTestResult = null
                    },
                    label = { Text(stringResource(R.string.server_url)) },
                    placeholder = { Text(stringResource(R.string.server_url_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isValidUrl(serverUrl) && serverUrl.isNotBlank(),
                    trailingIcon = {
                        if (serverUrl.isNotBlank() && isValidUrl(serverUrl)) {
                            Button(
                                onClick = {
                                    isTestingConnection = true
                                    scope.launch {
                                        connectionTestResult = testConnection(serverUrl)
                                        isTestingConnection = false
                                    }
                                },
                                enabled = !isTestingConnection
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.width(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(stringResource(R.string.test))
                                }
                            }
                        }
                    }
                )
                
                if (connectionTestResult != null) {
                    Text(
                        text = if (connectionTestResult == true) 
                            stringResource(R.string.connection_successful) 
                            else stringResource(R.string.connection_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connectionTestResult == true) 
                            MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.error
                    )
                }
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = serverType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.server_type)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        ServerType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    serverType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.username)) },
                    placeholder = { Text(stringResource(R.string.username_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    placeholder = { Text(stringResource(R.string.password_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible }
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) 
                                    stringResource(R.string.hide_password) 
                                    else stringResource(R.string.show_password)
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        val config = when (serverType) {
                            ServerType.SUBSONIC -> ServerConfig.Subsonic(
                                id = initialServer?.id ?: generateServerId(),
                                name = serverName.trim(),
                                baseUrl = serverUrl.trim(),
                                username = username.trim(),
                                priority = initialServer?.priority ?: 0,
                                isActive = initialServer?.isActive ?: false
                            )
                            ServerType.JELLYFIN -> ServerConfig.Jellyfin(
                                id = initialServer?.id ?: generateServerId(),
                                name = serverName.trim(),
                                baseUrl = serverUrl.trim(),
                                username = username.trim(),
                                priority = initialServer?.priority ?: 0,
                                isActive = initialServer?.isActive ?: false
                            )
                        }
                        onSave(config)
                    },
                    enabled = isValid
                ) {
                    Text(
                        text = if (initialServer == null) 
                            stringResource(R.string.add) 
                            else stringResource(R.string.save)
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun IconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.IconButton(onClick = onClick) {
        content()
    }
}

private fun validateServerData(
    name: String,
    url: String,
    username: String,
    password: String,
    isEditing: Boolean
): Boolean {
    return name.isNotBlank() &&
        isValidUrl(url) &&
        username.isNotBlank() &&
        (isEditing || password.isNotBlank())
}

private fun isValidUrl(url: String): Boolean {
    return try {
        val parsedUrl = URL(url)
        parsedUrl.protocol in listOf("http", "https") &&
            parsedUrl.host.isNotBlank()
    } catch (e: Exception) {
        false
    }
}

private suspend fun testConnection(url: String): Boolean {
    return try {
        val parsedUrl = URL(url)
        val host = parsedUrl.host
        val port = if (parsedUrl.port == -1) parsedUrl.defaultPort else parsedUrl.port
        
        return withTimeoutOrNull(5000) {
            try {
                InetAddress.getByName(host)
                true
            } catch (e: Exception) {
                false
            }
        } ?: false
    } catch (e: Exception) {
        false
    }
}

private fun generateServerId(): String {
    return "server_${System.currentTimeMillis()}"
}

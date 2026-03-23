package com.amberesaiae.melos.settings.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties

@Composable
fun CacheSizeDialog(
    cacheSize: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isClearing: Boolean = false,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = {
            if (!isClearing) onDismiss()
        },
        title = { Text("Clear cache?") },
        text = {
            Text(
                "Cache size: $cacheSize\n\n"
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isClearing
            ) {
                if (isClearing) {
                    CircularProgressIndicator(
                        modifier = Modifier.then(
                            if (isClearing) Modifier else Modifier
                        )
                    )
                } else {
                    Text("Clear")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isClearing
            ) {
                Text("Cancel")
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isClearing,
            dismissOnClickOutside = !isClearing
        )
    )
}

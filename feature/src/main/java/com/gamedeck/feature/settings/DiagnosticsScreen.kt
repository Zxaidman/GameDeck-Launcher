package com.gamedeck.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamedeck.core.diagnostics.CompleteDiagnostics

/**
 * Diagnostics screen showing device, backend, and session information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    diagnostics: CompleteDiagnostics?,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (diagnostics == null) {
                Text(
                    text = "No diagnostics available",
                    style = MaterialTheme.typography.bodyLarge
                )
                return@Column
            }

            // Device section
            Text(
                text = "Device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            DiagnosticsRow("Manufacturer", diagnostics.device.manufacturer)
            DiagnosticsRow("Model", diagnostics.device.model)
            DiagnosticsRow("Android", "${diagnostics.device.androidVersion} (API ${diagnostics.device.androidApi})")
            DiagnosticsRow("GameDeck", diagnostics.device.gameDeckVersion)

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Shizuku section
            Text(
                text = "Shizuku",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            DiagnosticsRow("State", diagnostics.shizuku.state.name)
            DiagnosticsRow("Privilege", diagnostics.shizuku.privilegeLevel)
            DiagnosticsRow("Permission", if (diagnostics.shizuku.permissionGranted) "Granted" else "Not granted")
            DiagnosticsRow("UserService", if (diagnostics.shizuku.userServiceStarted) "Started" else "Stopped")

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Session section
            Text(
                text = "Session",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            DiagnosticsRow("Foreground", diagnostics.session.currentForegroundPackage ?: "Unknown")
            DiagnosticsRow("Profile", diagnostics.session.selectedProfile ?: "None")
            DiagnosticsRow("Layout", diagnostics.session.currentLayout ?: "None")
            DiagnosticsRow("Scaling", diagnostics.session.currentScalingMode ?: "None")
            DiagnosticsRow("Backend", diagnostics.session.activeBackend ?: "None")
            DiagnosticsRow("Overlay", if (diagnostics.session.overlayActive) "Active" else "Inactive")

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Backends section
            Text(
                text = "Input Backends",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            diagnostics.backends.forEach { backend ->
                DiagnosticsRow(
                    label = backend.backendId,
                    value = if (backend.available) "Available" else "Unavailable"
                )
                backend.capabilities.forEach { capability ->
                    Text(
                        text = "  • ${capability.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (backend.reasonUnavailable != null) {
                    Text(
                        text = "  Reason: ${backend.reasonUnavailable}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * A single diagnostics row with label and value.
 */
@Composable
fun DiagnosticsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
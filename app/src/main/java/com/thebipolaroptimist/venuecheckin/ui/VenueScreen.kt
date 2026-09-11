package com.thebipolaroptimist.venuecheckin.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.thebipolaroptimist.venuecheckin.domain.displayName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Requested as one batch - safe to bundle since none of these are auto-denied when requested
// together (unlike background location, which Android requires as a separate, later request -
// see below). BLUETOOTH_SCAN only exists from API 31; POST_NOTIFICATIONS only from API 33 (below
// that, notifications don't need runtime permission at all).
private fun foregroundPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

private fun permissionLabel(permission: String): String = when (permission) {
    Manifest.permission.ACCESS_FINE_LOCATION -> "Location"
    Manifest.permission.BLUETOOTH_SCAN -> "Nearby devices (Bluetooth scan)"
    Manifest.permission.ACCESS_BACKGROUND_LOCATION -> "Background location"
    Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
    else -> permission
}

private fun permissionReason(permission: String): String = when (permission) {
    Manifest.permission.ACCESS_FINE_LOCATION -> "geofencing and beacon scanning won't work at all"
    Manifest.permission.BLUETOOTH_SCAN -> "beacon scanning won't work"
    Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
        "geofence transitions won't be detected while the app is backgrounded"
    Manifest.permission.POST_NOTIFICATIONS ->
        "the monitoring notification won't show while scanning runs in the background"
    else -> "some functionality may not work"
}

@Composable
fun VenueScreen(viewModel: VenueViewModel = hiltViewModel()) {
    val currentReading by viewModel.currentReading.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val log by viewModel.log.collectAsState()
    val geofenceLog by viewModel.geofenceLog.collectAsState()

    val context = LocalContext.current

    fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun currentlyMissing() =
        (foregroundPermissions().toList() + Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            .filterNot(::isGranted)

    var missingPermissions by remember { mutableStateOf(currentlyMissing()) }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { missingPermissions = currentlyMissing() }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        missingPermissions = currentlyMissing()
        if (foregroundPermissions().all(::isGranted)) {
            viewModel.registerGeofences()
            if (!isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    // Foreground first; background location only once foreground is actually granted (staged
    // per requirement 8 - see foregroundPermissions() comment above).
    fun requestMissingPermissions() {
        val missingForeground = foregroundPermissions().filterNot(::isGranted)
        if (missingForeground.isNotEmpty()) {
            foregroundLauncher.launch(missingForeground.toTypedArray())
        } else if (!isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    LaunchedEffect(Unit) {
        if (foregroundPermissions().all(::isGranted)) {
            viewModel.registerGeofences()
            if (!isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        } else {
            foregroundLauncher.launch(foregroundPermissions())
        }
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = currentReading,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Sanity-check indicator, mostly for debugging
            Text(
                text = if (isScanning) "● Scanning" else "○ Not scanning",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (missingPermissions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Missing permissions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                for (permission in missingPermissions) {
                    Text(
                        text = "• ${permissionLabel(permission)} — ${permissionReason(permission)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = { requestMissingPermissions() }) {
                    Text("Fix permissions")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Geofence Log", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(4.dp))

            Column {
                for (entry in geofenceLog) {
                    Text(
                        text = "${timeFormatter.format(Date(entry.timestampMillis))}   ${entry.message}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(text = "Beacon Log", style = MaterialTheme.typography.titleMedium)

            Spacer(modifier = Modifier.height(4.dp))

            Column {
                for (entry in log) {
                    val distanceText = entry.distanceMeters?.let { "~${"%.1f".format(it)} m" } ?: "-"
                    Text(
                        text = "${timeFormatter.format(Date(entry.timestampMillis))}   " +
                            "${entry.proximity.displayName()}   $distanceText",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VenueScreen(viewModel: VenueViewModel = hiltViewModel()) {
    val isScanning by viewModel.isScanning.collectAsState()
    val currentReading by viewModel.currentReading.collectAsState()
    val log by viewModel.log.collectAsState()
    val geofenceLog by viewModel.geofenceLog.collectAsState()

    val context = LocalContext.current

    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    fun hasAllScanPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        if (hasAllScanPermissions()) viewModel.toggleScanning()
    }

    // Geofencing only needs fine location, not BLUETOOTH_SCAN - kept as its own permission check
    // and launcher, separate from the BLE one above, matching the "separate from beacon" ask.
    // Background location (needed for transitions to fire reliably once the app isn't in the
    // foreground) isn't requested here yet - planning.md §11's staged permission flow is still
    // unbuilt; this is foreground-only manual verification for now.
    fun hasFineLocationPermission() =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    val geofencePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.registerGeofences() }

    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            Text(
                text = currentReading,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (isScanning || hasAllScanPermissions()) {
                        viewModel.toggleScanning()
                    } else {
                        permissionLauncher.launch(requiredPermissions)
                    }
                },
            ) {
                Text(if (isScanning) "Stop Scanning" else "Start Scanning")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (hasFineLocationPermission()) {
                        viewModel.registerGeofences()
                    } else {
                        geofencePermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
            ) {
                Text("Register Geofences")
            }

            Spacer(modifier = Modifier.height(20.dp))

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

package com.thebipolaroptimist.venuecheckin.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.thebipolaroptimist.venuecheckin.R
import dagger.hilt.android.AndroidEntryPoint

private const val NOTIFICATION_CHANNEL_ID = "venue_monitoring"
private const val NOTIFICATION_ID = 1

@AndroidEntryPoint
class VenueMonitorService : Service() {

    override fun onCreate() {
        super.onCreate()
        // startForeground() must be called immediately, before any  async setup, or this risks
        // ForegroundServiceDidNotStartInTimeException.
        //
        // Note: on API 34+ this also requires the app to already hold at least one of
        // BLUETOOTH_CONNECT/ADVERTISE/SCAN (we declare BLUETOOTH_SCAN) at call time for the
        // "connectedDevice" foreground service type - permission staging/requesting is a separate,
        // not-yet-built piece (planning.md §11), not handled here.
        createNotificationChannelIfNeeded()
        startForeground(NOTIFICATION_ID, buildNotification())

        // TODO: start BLE scanning here (BleScanner + VenueStateMachine) once the standalone
        // BLE-scanning path (currently a manual-button test harness in VenueViewModel) and this
        // geofencing path have each been independently verified. Deliberately not wired together
        // yet - see planning.md's scaffolding notes.
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: stop BLE scanning here once it's started in onCreate above.
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Venue monitoring",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Venue Check-In")
            .setContentText("Monitoring venue proximity")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
}

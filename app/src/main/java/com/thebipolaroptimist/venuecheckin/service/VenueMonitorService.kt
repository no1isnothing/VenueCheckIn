package com.thebipolaroptimist.venuecheckin.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VenueMonitorService : Service() {

    override fun onCreate() {
        super.onCreate()
        // TODO: notification channel + startForeground()
        // planning.md §8 / DECISIONS.md — foreground-only-while-Inside is decided;
        // must call startForeground() immediately, no async setup first
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

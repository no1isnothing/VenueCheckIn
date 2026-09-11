package com.thebipolaroptimist.venuecheckin.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        TODO(
            "planning.md §5 — parse GeofencingEvent, start/stop VenueMonitorService; " +
                "see startForegroundService-from-receiver gotcha",
        )
    }
}

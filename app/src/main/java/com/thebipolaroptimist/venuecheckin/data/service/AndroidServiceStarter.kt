package com.thebipolaroptimist.venuecheckin.data.service

import android.content.Context
import android.content.Intent
import com.thebipolaroptimist.venuecheckin.service.VenueMonitorService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context,
) : ServiceStarter {

    // Only called from a foreground-triggered context
    override fun startMonitoring() {
        context.startForegroundService(Intent(context, VenueMonitorService::class.java))
    }

    override fun stopMonitoring() {
        context.stopService(Intent(context, VenueMonitorService::class.java))
    }
}

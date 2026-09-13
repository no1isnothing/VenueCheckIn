package com.thebipolaroptimist.venuecheckin.di

import android.app.Application
import com.thebipolaroptimist.venuecheckin.domain.VenueMonitor
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class VenueCheckInApplication : Application() {

    // Not otherwise referenced - injecting it here forces Hilt to construct the VenueMonitor
    // singleton at process start rather than lazily. See DECISIONS.md.
    @Inject
    lateinit var venueMonitor: VenueMonitor

    override fun onCreate() {
        super.onCreate()

        // Plant DebugTree only in Debug mode
        //if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        //}

        // Referencing it confirms eager construction happened here
        Timber.i("VenueMonitor eagerly initialized at process start: $venueMonitor")
    }
}

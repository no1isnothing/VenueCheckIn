package com.thebipolaroptimist.venuecheckin.di

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class VenueCheckInApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Plant DebugTree only in Debug mode
        //if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        //}
    }
}

package com.thebipolaroptimist.venuecheckin.data.location

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.thebipolaroptimist.venuecheckin.domain.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FusedLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationSource {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    override suspend fun currentLocation(): GeoPoint? {
        // TODO: use kotlinx-coroutines-play-services Task.await()
        TODO("planning.md §4 — current-location lookup not yet implemented")
    }
}

package com.thebipolaroptimist.venuecheckin.data.location

import com.thebipolaroptimist.venuecheckin.domain.GeoPoint

interface LocationSource {
    suspend fun currentLocation(): GeoPoint?
}

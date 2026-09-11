package com.thebipolaroptimist.venuecheckin.fakes

import com.thebipolaroptimist.venuecheckin.data.location.LocationSource
import com.thebipolaroptimist.venuecheckin.domain.GeoPoint

class FakeLocationSource : LocationSource {

    var location: GeoPoint? = null

    override suspend fun currentLocation(): GeoPoint? = location
}

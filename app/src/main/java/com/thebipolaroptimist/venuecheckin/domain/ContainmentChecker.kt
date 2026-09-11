package com.thebipolaroptimist.venuecheckin.domain

import com.thebipolaroptimist.venuecheckin.data.venue.Venue
import javax.inject.Inject

class ContainmentChecker @Inject constructor() {

    fun isWithin(point: GeoPoint, venue: Venue): Boolean {
        TODO("planning.md §4 — already-inside approach still pending")
    }
}

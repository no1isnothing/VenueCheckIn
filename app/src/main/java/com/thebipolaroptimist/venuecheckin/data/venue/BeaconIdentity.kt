package com.thebipolaroptimist.venuecheckin.data.venue

import java.util.UUID

// iBeacon identity — current pick, see planning.md §3. Eddystone-UID (namespace/instance)
// is the documented alternative if that decision changes.
data class BeaconIdentity(
    val uuid: UUID,
    val major: Int,
    val minor: Int,
)

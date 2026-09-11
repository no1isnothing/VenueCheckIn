package com.thebipolaroptimist.venuecheckin.domain

import javax.inject.Inject

class VenueStateMachine @Inject constructor() {

    fun reduce(current: VenueState, event: VenueEvent): VenueState {
        TODO(
            "planning.md §7 — must be idempotent on duplicate ENTER/EXIT for the same venue; " +
                "EXIT must synchronously stop scanning, see planning.md §7",
        )
    }
}

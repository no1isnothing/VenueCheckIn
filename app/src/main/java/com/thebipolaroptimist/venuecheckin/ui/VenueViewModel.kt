package com.thebipolaroptimist.venuecheckin.ui

import androidx.lifecycle.ViewModel
import com.thebipolaroptimist.venuecheckin.data.ble.BleScanner
import com.thebipolaroptimist.venuecheckin.data.geofence.GeofenceSource
import com.thebipolaroptimist.venuecheckin.data.location.LocationSource
import com.thebipolaroptimist.venuecheckin.data.venue.VenueRepository
import com.thebipolaroptimist.venuecheckin.domain.VenueState
import com.thebipolaroptimist.venuecheckin.domain.VenueStateMachine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class VenueViewModel @Inject constructor(
    private val venueRepository: VenueRepository,
    private val geofenceSource: GeofenceSource,
    private val bleScanner: BleScanner,
    private val locationSource: LocationSource,
    private val stateMachine: VenueStateMachine,
) : ViewModel() {

    private val _state = MutableStateFlow<VenueState>(VenueState.Outside)
    val state: StateFlow<VenueState> = _state.asStateFlow()
}

package com.thebipolaroptimist.venuecheckin.data.service

// Abstraction over starting/stopping VenueMonitorService, so VenueMonitor doesn't need a raw
// Context and stays unit-testable without Robolectric
interface ServiceStarter {
    fun startMonitoring()
    fun stopMonitoring()
}

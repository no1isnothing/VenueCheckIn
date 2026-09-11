package com.thebipolaroptimist.venuecheckin.di

import com.thebipolaroptimist.venuecheckin.data.ble.BleScanner
import com.thebipolaroptimist.venuecheckin.data.ble.PlatformBleScanner
import com.thebipolaroptimist.venuecheckin.data.geofence.GeofenceSource
import com.thebipolaroptimist.venuecheckin.data.geofence.PlayServicesGeofenceSource
import com.thebipolaroptimist.venuecheckin.data.location.FusedLocationSource
import com.thebipolaroptimist.venuecheckin.data.location.LocationSource
import com.thebipolaroptimist.venuecheckin.data.permission.AndroidPermissionChecker
import com.thebipolaroptimist.venuecheckin.data.permission.PermissionChecker
import com.thebipolaroptimist.venuecheckin.data.service.AndroidServiceStarter
import com.thebipolaroptimist.venuecheckin.data.service.ServiceStarter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindGeofenceSource(impl: PlayServicesGeofenceSource): GeofenceSource

    @Binds
    abstract fun bindBleScanner(impl: PlatformBleScanner): BleScanner

    @Binds
    abstract fun bindLocationSource(impl: FusedLocationSource): LocationSource

    @Binds
    abstract fun bindPermissionChecker(impl: AndroidPermissionChecker): PermissionChecker

    @Binds
    abstract fun bindServiceStarter(impl: AndroidServiceStarter): ServiceStarter
}

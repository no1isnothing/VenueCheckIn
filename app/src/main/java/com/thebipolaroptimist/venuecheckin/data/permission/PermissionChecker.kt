package com.thebipolaroptimist.venuecheckin.data.permission

// Abstraction over ContextCompat.checkSelfPermission so callers that need to check permission
// state (e.g. VenueViewModel deciding whether it's safe to start a BLE scan) stay unit-testable
interface PermissionChecker {
    fun isGranted(permission: String): Boolean
}

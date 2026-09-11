package com.thebipolaroptimist.venuecheckin.fakes

import com.thebipolaroptimist.venuecheckin.data.permission.PermissionChecker

class FakePermissionChecker(
    var granted: Set<String> = emptySet(),
) : PermissionChecker {

    override fun isGranted(permission: String): Boolean = permission in granted
}

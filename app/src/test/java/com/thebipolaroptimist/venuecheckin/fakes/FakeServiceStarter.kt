package com.thebipolaroptimist.venuecheckin.fakes

import com.thebipolaroptimist.venuecheckin.data.service.ServiceStarter

class FakeServiceStarter : ServiceStarter {

    var isMonitoring: Boolean = false
        private set

    override fun startMonitoring() {
        isMonitoring = true
    }

    override fun stopMonitoring() {
        isMonitoring = false
    }
}

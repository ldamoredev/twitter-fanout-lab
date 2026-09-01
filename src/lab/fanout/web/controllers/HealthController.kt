package lab.fanout.web.controllers

import dev.botta.trantor.web.application.ApplicationController
import dev.botta.trantor.web.application.routes.ApplicationRouteRegister
import lab.fanout.core.health.Ping

class HealthController: ApplicationController {
    override fun registerRoutes(http: ApplicationRouteRegister) {
        http.get<Ping>("/health")
    }
}

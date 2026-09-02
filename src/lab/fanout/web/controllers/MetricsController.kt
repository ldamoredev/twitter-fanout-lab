package lab.fanout.web.controllers

import dev.botta.trantor.web.application.ApplicationController
import dev.botta.trantor.web.application.routes.ApplicationRouteRegister
import lab.fanout.core.timelines.GetFanoutStats

class MetricsController: ApplicationController {
    override fun registerRoutes(http: ApplicationRouteRegister) {
        http.get<GetFanoutStats>("/metrics/fanout")
    }
}

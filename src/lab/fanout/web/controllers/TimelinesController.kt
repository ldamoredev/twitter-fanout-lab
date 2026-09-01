package lab.fanout.web.controllers

import dev.botta.trantor.web.application.ApplicationController
import dev.botta.trantor.web.application.routes.ApplicationRouteRegister
import lab.fanout.core.timelines.GetTimeline

class TimelinesController: ApplicationController {
    override fun registerRoutes(http: ApplicationRouteRegister) {
        http.get<GetTimeline>("/timelines/{userId}")
    }
}

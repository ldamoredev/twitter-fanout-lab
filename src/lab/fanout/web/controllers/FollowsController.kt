package lab.fanout.web.controllers

import dev.botta.trantor.web.application.ApplicationController
import dev.botta.trantor.web.application.routes.ApplicationRouteRegister
import lab.fanout.core.follows.FollowUser

class FollowsController: ApplicationController {
    override fun registerRoutes(http: ApplicationRouteRegister) {
        http.post<FollowUser>("/follows")
    }
}

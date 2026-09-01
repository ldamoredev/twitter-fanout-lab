package lab.fanout.web.controllers

import dev.botta.trantor.web.application.ApplicationController
import dev.botta.trantor.web.application.routes.ApplicationRouteRegister
import lab.fanout.core.posts.GetPost
import lab.fanout.core.posts.PublishPost

class PostsController: ApplicationController {
    override fun registerRoutes(http: ApplicationRouteRegister) {
        http.post<PublishPost>("/posts", 201)
        http.get<GetPost>("/posts/{postId}")
    }
}

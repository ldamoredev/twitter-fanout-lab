package lab.fanout.web

import dev.botta.trantor.config.Config
import dev.botta.trantor.config.ConfigManager
import dev.botta.trantor.di.ServiceProvider
import dev.botta.trantor.di.ServiceRegistry
import dev.botta.trantor.hosting.Module
import dev.botta.trantor.hosting.addModule
import dev.botta.trantor.web.application.WebApplication
import dev.botta.trantor.web.server.HttpServerSettings
import io.javalin.http.staticfiles.Location
import lab.fanout.core.CoreModule
import lab.fanout.web.controllers.FollowsController
import lab.fanout.web.controllers.HealthController
import lab.fanout.web.controllers.MetricsController
import lab.fanout.web.controllers.PostsController
import lab.fanout.web.controllers.TimelinesController

class TwitterFanoutWebModule: Module {
    override fun compose(services: ServiceRegistry, config: ConfigManager) {
        services.addModule<CoreModule>()
        services.configure<HttpServerSettings> { settings, _ ->
            settings.configureJavalin = { javalin ->
                javalin.staticFiles.add("/public", Location.CLASSPATH)
            }
        }
    }

    override fun initialize(services: ServiceProvider, config: Config) {
        val app = services.get<WebApplication>()
        app.addController(HealthController())
        app.addController(PostsController())
        app.addController(FollowsController())
        app.addController(TimelinesController())
        app.addController(MetricsController())
    }
}

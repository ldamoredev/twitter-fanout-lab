package lab.fanout.core

import dev.botta.cqbus.CQBus
import dev.botta.trantor.config.Config
import dev.botta.trantor.config.ConfigManager
import dev.botta.trantor.di.ServiceProvider
import dev.botta.trantor.di.ServiceRegistry
import dev.botta.trantor.hosting.Module
import lab.fanout.core.follows.FollowUser
import lab.fanout.core.follows.Follows
import lab.fanout.core.follows.InMemoryFollows
import lab.fanout.core.health.Ping
import lab.fanout.core.posts.GetPost
import lab.fanout.core.posts.InMemoryPosts
import lab.fanout.core.posts.Posts
import lab.fanout.core.posts.PublishPost
import lab.fanout.core.timelines.GetTimeline
import lab.fanout.core.timelines.InMemoryTimelines
import lab.fanout.core.timelines.Timelines

class CoreModule: Module {
    override fun compose(services: ServiceRegistry, config: ConfigManager) {
        services.addSingleton<Posts, InMemoryPosts>()
        services.addSingleton<Follows, InMemoryFollows>()
        services.addSingleton<Timelines, InMemoryTimelines>()
    }

    override fun initialize(services: ServiceProvider, config: Config) {
        val bus = services.get<CQBus>()
        bus.registerHandler { Ping.Handler() }
        bus.registerHandler { services.create<PublishPost.Handler>() }
        bus.registerHandler { services.create<GetPost.Handler>() }
        bus.registerHandler { services.create<FollowUser.Handler>() }
        bus.registerHandler { services.create<GetTimeline.Handler>() }
    }
}

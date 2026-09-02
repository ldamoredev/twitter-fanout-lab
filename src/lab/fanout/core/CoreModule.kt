package lab.fanout.core

import dev.botta.cqbus.CQBus
import dev.botta.trantor.config.Config
import dev.botta.trantor.config.ConfigManager
import dev.botta.trantor.core.jobs.JobDispatcher
import dev.botta.trantor.core.jobs.JobQueueRegistry
import dev.botta.trantor.core.jobs.addJobProcessor
import dev.botta.trantor.core.jobs.registerHandler
import dev.botta.trantor.di.ServiceProvider
import dev.botta.trantor.di.ServiceRegistry
import dev.botta.trantor.hosting.Module
import dev.botta.trantor.primitives.events.EventDispatcher
import dev.botta.trantor.primitives.events.on
import lab.fanout.core.follows.FollowUser
import lab.fanout.core.follows.Follows
import lab.fanout.core.follows.InMemoryFollows
import lab.fanout.core.health.Ping
import lab.fanout.core.posts.GetPost
import lab.fanout.core.posts.InMemoryPosts
import lab.fanout.core.posts.PostCache
import lab.fanout.core.posts.PostPublished
import lab.fanout.core.posts.Posts
import lab.fanout.core.posts.PublishPost
import lab.fanout.core.timelines.CelebrityThreshold
import lab.fanout.core.timelines.FANOUT_QUEUE_NAME
import lab.fanout.core.timelines.FANOUT_WORKERS
import lab.fanout.core.timelines.FanoutPost
import lab.fanout.core.timelines.FanoutStatsSource
import lab.fanout.core.timelines.GetFanoutStats
import lab.fanout.core.timelines.GetTimeline
import lab.fanout.core.timelines.InMemoryTimelines
import lab.fanout.core.timelines.Timelines
import lab.fanout.core.timelines.WriteTimelineChunk
import lab.fanout.platform.queues.InMemoryMessageQueue
import lab.fanout.platform.queues.QueueFanoutStats

class CoreModule: Module {
    override fun compose(services: ServiceRegistry, config: ConfigManager) {
        services.addSingleton<Posts, InMemoryPosts>()
        services.addSingleton<Follows, InMemoryFollows>()
        services.addSingleton<Timelines, InMemoryTimelines>()
        // CacheModule sólo registra la factory. El cache de posts lo construye el lab.
        services.addSingleton<PostCache, PostCache>()
        // El umbral se resuelve por DI para que `fanout.celebrityThresholdFollowers` lo pueda mover.
        services.addSingleton<CelebrityThreshold, CelebrityThreshold>()

        // Trantor publica un solo driver de cola y es SQS. La del lab vive en el proceso.
        services.addSingleton(InMemoryMessageQueue(FANOUT_QUEUE_NAME))
        services.addSingleton<FanoutStatsSource, QueueFanoutStats>()
        services.configure<JobQueueRegistry> { registry, provider ->
            registry.addQueue(FANOUT_QUEUE_NAME, provider.get<InMemoryMessageQueue>())
        }
        // Segundo HostedService del lab, después del HttpServer: el que consume el fan-out.
        services.addJobProcessor(FANOUT_QUEUE_NAME, FANOUT_WORKERS)
    }

    override fun initialize(services: ServiceProvider, config: Config) {
        val bus = services.get<CQBus>()
        bus.registerHandler { Ping.Handler() }
        bus.registerHandler { services.create<PublishPost.Handler>() }
        bus.registerHandler { services.create<GetPost.Handler>() }
        bus.registerHandler { services.create<FollowUser.Handler>() }
        bus.registerHandler { services.create<GetTimeline.Handler>() }
        bus.registerHandler { services.create<GetFanoutStats.Handler>() }

        val jobs = services.get<JobDispatcher>()
        jobs.registerHandler(services.create<FanoutPost.Handler>())
        jobs.registerHandler(services.create<WriteTimelineChunk.Handler>())

        // El fan-out sale del evento, no del command: así `defer` puede atrasarlo hasta que
        // el post esté persistido y cacheado. `jobs.dispatch` adentro de `defer` no se atrasa.
        services.get<EventDispatcher>().on<PostPublished> { event ->
            jobs.dispatch(FanoutPost(event.postId, event.authorId))
        }
    }
}

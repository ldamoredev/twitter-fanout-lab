package lab.fanout.core.outbox

import dev.botta.trantor.core.jobs.JobDispatcher
import dev.botta.trantor.primitives.events.Event
import dev.botta.trantor.primitives.events.QueuedEventHandler
import lab.fanout.core.posts.PostPublished
import lab.fanout.core.timelines.FANOUT_QUEUE_NAME
import lab.fanout.core.timelines.FanoutPost

/**
 * Tiene que ser una clase con nombre: un `events.on { }` anónimo no tiene `handlerType`
 * y `ProcessEventHandlerJob` no puede rehidratarlo. `queued` manda el efecto a la cola;
 * `afterCommit` (default true) lo atrasa hasta el commit.
 */
class FanoutOnPostPublished(private val jobs: JobDispatcher): QueuedEventHandler(FANOUT_QUEUE_NAME) {
    override val eventTypes = listOf(PostPublished::class)

    override fun on(event: Event) {
        val published = event as PostPublished
        jobs.dispatch(FanoutPost(published.postId, published.authorId))
    }
}

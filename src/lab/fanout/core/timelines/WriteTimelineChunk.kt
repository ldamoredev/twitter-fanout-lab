package lab.fanout.core.timelines

import dev.botta.trantor.core.jobs.Job
import dev.botta.trantor.core.jobs.JobHandler
import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.PostId

/**
 * Fan-out on write, segundo nivel: la escritura de verdad. Viaja el `PostId` y nada más — 16 bytes
 * por seguidor, que es el cálculo de S1. El texto se hidrata al leer (S4).
 *
 * Si el job falla, la cola lo vuelve a entregar: `prepend` deduplica, así que reprocesar no
 * duplica ids en el timeline.
 */
data class WriteTimelineChunk(val postId: PostId, val followerIds: List<UserId>): Job() {
    // El JobProcessor loguea el job entero en INFO: sin esto, cada job escupe 100 UUIDs.
    override fun toString() = "WriteTimelineChunk(postId=$postId, followers=${followerIds.size})"

    internal class Handler(private val timelines: Timelines): JobHandler<WriteTimelineChunk> {
        override fun execute(job: WriteTimelineChunk) {
            job.followerIds.forEach { timelines.prepend(it, job.postId) }
        }
    }
}

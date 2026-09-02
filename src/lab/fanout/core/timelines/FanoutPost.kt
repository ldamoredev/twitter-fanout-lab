package lab.fanout.core.timelines

import dev.botta.trantor.core.jobs.Job
import dev.botta.trantor.core.jobs.JobDispatcher
import dev.botta.trantor.core.jobs.JobHandler
import lab.fanout.core.follows.Follows
import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.PostId

/**
 * Fan-out on write, primer nivel: publicar despacha *un* job, no uno por seguidor. Leer la lista
 * de seguidores en el request haría que publicar cueste O(seguidores) antes de contestar el 201.
 *
 * El handler pagina esa lista y despacha un `WriteTimelineChunk` por cada tanda, que es lo que
 * habilita workers concurrentes.
 */
data class FanoutPost(val postId: PostId, val authorId: UserId): Job() {
    internal class Handler(private val follows: Follows, private val jobs: JobDispatcher): JobHandler<FanoutPost> {
        override fun execute(job: FanoutPost) {
            follows.followersOf(job.authorId)
                .chunked(FANOUT_CHUNK_FOLLOWERS)
                .forEach { jobs.dispatch(WriteTimelineChunk(job.postId, it)) }
        }
    }
}

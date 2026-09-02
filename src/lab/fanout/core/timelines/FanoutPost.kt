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
 *
 * Desde S3 el fan-out no es incondicional: si el autor pasa el umbral de celebridad, este job
 * no despacha nada y el post se entrega al leer (`GetTimeline`).
 */
data class FanoutPost(val postId: PostId, val authorId: UserId): Job() {
    internal class Handler(
        private val follows: Follows,
        private val jobs: JobDispatcher,
        private val celebrity: CelebrityThreshold,
    ): JobHandler<FanoutPost> {
        override fun execute(job: FanoutPost) {
            // Contar antes de listar: para una celebridad, traer la lista es traer millones de ids.
            if (celebrity.exceededBy(follows.followersCount(job.authorId))) return

            follows.followersOf(job.authorId)
                .chunked(FANOUT_CHUNK_FOLLOWERS)
                .forEach { jobs.dispatch(WriteTimelineChunk(job.postId, it)) }
        }
    }
}

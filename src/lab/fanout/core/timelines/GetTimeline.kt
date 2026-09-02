package lab.fanout.core.timelines

import dev.botta.cqbus.identity.Identity
import dev.botta.cqbus.requests.Query
import dev.botta.cqbus.requests.handlers.RequestHandler
import lab.fanout.core.follows.Follows
import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.PostCache
import lab.fanout.core.posts.PostId
import lab.fanout.core.posts.Posts

/**
 * Lectura hidratada (S4). Los ids siguen viniendo del híbrido (S3): timeline precomputado,
 * posts recientes de las celebridades seguidas, y los del propio lector — ese último es el
 * camino de read-your-writes: el autor ve lo suyo sin esperar al fan-out.
 *
 * Hidratar es `PostId → Post` vía `PostCache`. El merge sigue ordenando por UUIDv7, sin
 * mirar `createdAt`.
 */
data class GetTimeline(val userId: UserId): Query<GetTimeline.Feed> {
    data class TimelinePost(val postId: PostId, val authorId: UserId, val text: String)
    data class Feed(val posts: List<TimelinePost>)

    internal class Handler(
        private val timelines: Timelines,
        private val follows: Follows,
        private val posts: Posts,
        private val celebrity: CelebrityThreshold,
        private val cache: PostCache,
    ): RequestHandler<GetTimeline, Feed> {
        override fun execute(request: GetTimeline, identity: Identity): Feed {
            val precomputed = timelines.idsOf(request.userId)
            val pulled = celebritiesFollowedBy(request.userId)
                .flatMap { posts.recentBy(it, CELEBRITY_MERGE_POSTS) }
            val own = posts.recentBy(request.userId, TIMELINE_WINDOW_POSTS)

            return Feed(merge(precomputed, pulled + own).map(::hydrate))
        }

        private fun celebritiesFollowedBy(userId: UserId) = follows.followeesOf(userId)
            .filter { celebrity.exceededBy(follows.followersCount(it)) }

        // distinct() porque un autor que cruzó el umbral después de publicar cae en los dos lados,
        // y porque el propio post puede estar también prependeado en el timeline del autor.
        private fun merge(precomputed: List<PostId>, pulled: List<PostId>) = (precomputed + pulled)
            .distinct()
            .sortedByDescending { it.toUUID() }
            .take(TIMELINE_WINDOW_POSTS)

        private fun hydrate(id: PostId): TimelinePost {
            val post = cache.get(id) { posts.get(it) }
            return TimelinePost(post.id, post.authorId, post.text)
        }
    }
}

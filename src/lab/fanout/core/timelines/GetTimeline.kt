package lab.fanout.core.timelines

import dev.botta.cqbus.identity.Identity
import dev.botta.cqbus.requests.Query
import dev.botta.cqbus.requests.handlers.RequestHandler
import lab.fanout.core.follows.Follows
import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.PostId
import lab.fanout.core.posts.Posts

/**
 * El lado de lectura del híbrido (S3): el timeline precomputado por el fan-out más los posts
 * recientes de las celebridades que el lector sigue, que nadie le escribió.
 *
 * El merge ordena por `PostId` y no hidrata: los `Id` de Trantor son UUIDv7, o sea que el
 * timestamp está en los bits altos y ordenar ids es ordenar por fecha. Traer el `Post` para
 * mirarle el `createdAt` sería hidratar, y eso es S4.
 */
data class GetTimeline(val userId: UserId): Query<GetTimeline.Feed> {
    data class Feed(val postIds: List<PostId>)

    internal class Handler(
        private val timelines: Timelines,
        private val follows: Follows,
        private val posts: Posts,
        private val celebrity: CelebrityThreshold,
    ): RequestHandler<GetTimeline, Feed> {
        override fun execute(request: GetTimeline, identity: Identity): Feed {
            val precomputed = timelines.idsOf(request.userId)
            val pulled = celebritiesFollowedBy(request.userId)
                .flatMap { posts.recentBy(it, CELEBRITY_MERGE_POSTS) }

            return Feed(merge(precomputed, pulled))
        }

        private fun celebritiesFollowedBy(userId: UserId) = follows.followeesOf(userId)
            .filter { celebrity.exceededBy(follows.followersCount(it)) }

        // distinct() porque un autor que cruzó el umbral después de publicar cae en los dos lados.
        private fun merge(precomputed: List<PostId>, pulled: List<PostId>) = (precomputed + pulled)
            .distinct()
            .sortedByDescending { it.toUUID() }
            .take(TIMELINE_WINDOW_POSTS)
    }
}

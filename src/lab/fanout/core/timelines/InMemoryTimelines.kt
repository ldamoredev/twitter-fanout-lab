package lab.fanout.core.timelines

import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.PostId
import java.util.concurrent.ConcurrentHashMap

class InMemoryTimelines: Timelines {
    private val idsByOwner = ConcurrentHashMap<UserId, List<PostId>>()

    override fun prepend(ownerId: UserId, postId: PostId) {
        idsByOwner.compute(ownerId) { _, current ->
            val withoutDup = (current ?: emptyList()).filterNot { it == postId }
            (listOf(postId) + withoutDup).take(TIMELINE_WINDOW_POSTS)
        }
    }

    override fun idsOf(ownerId: UserId): List<PostId> = idsByOwner[ownerId] ?: emptyList()
}

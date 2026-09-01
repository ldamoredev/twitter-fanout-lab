package lab.fanout.core.timelines

import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.PostId

interface Timelines {
    fun prepend(ownerId: UserId, postId: PostId)
    fun idsOf(ownerId: UserId): List<PostId>
}

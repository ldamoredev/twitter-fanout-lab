package lab.fanout.core.posts

import dev.botta.trantor.domain.errors.NotFoundError
import java.util.concurrent.ConcurrentHashMap

class InMemoryPosts: Posts {
    private val byId = ConcurrentHashMap<PostId, Post>()

    override fun add(post: Post) {
        byId[post.id] = post
    }

    override fun get(id: PostId): Post =
        byId[id] ?: throw NotFoundError("Post $id not found")
}

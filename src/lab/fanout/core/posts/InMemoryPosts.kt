package lab.fanout.core.posts

import dev.botta.trantor.domain.errors.NotFoundError
import lab.fanout.core.identity.UserId
import lab.fanout.core.timelines.TIMELINE_WINDOW_POSTS
import java.util.concurrent.ConcurrentHashMap

class InMemoryPosts: Posts {
    private val byId = ConcurrentHashMap<PostId, Post>()
    // El índice por autor guarda la ventana de lectura, no el archivo completo del usuario.
    private val idsByAuthor = ConcurrentHashMap<UserId, List<PostId>>()

    override fun add(post: Post) {
        byId[post.id] = post
        idsByAuthor.compute(post.authorId) { _, current ->
            (listOf(post.id) + (current ?: emptyList())).take(TIMELINE_WINDOW_POSTS)
        }
    }

    override fun get(id: PostId): Post =
        byId[id] ?: throw NotFoundError("Post $id not found")

    override fun recentBy(authorId: UserId, limit: Int): List<PostId> =
        idsByAuthor[authorId]?.take(limit) ?: emptyList()
}

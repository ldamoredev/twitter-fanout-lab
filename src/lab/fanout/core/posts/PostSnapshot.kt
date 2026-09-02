package lab.fanout.core.posts

import lab.fanout.core.identity.UserId
import java.time.LocalDateTime

/**
 * Lo que entra al `InMemoryCache`. Trantor pide snapshots, no entidades mutables: el `Post` de
 * dominio no muta, pero el contrato del cache es "un valor inmutable serializable", no el objeto
 * vivo. Hidratar reconstruye el `Post` desde acá.
 */
data class PostSnapshot(
    val id: PostId,
    val authorId: UserId,
    val text: String,
    val createdAt: LocalDateTime,
) {
    fun toPost() = Post(id = id, authorId = authorId, text = text, createdAt = createdAt)

    companion object {
        fun of(post: Post) = PostSnapshot(post.id, post.authorId, post.text, post.createdAt)
    }
}

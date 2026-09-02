package lab.fanout.core.posts

import lab.fanout.core.identity.UserId

interface Posts {
    fun add(post: Post)
    fun get(id: PostId): Post

    /** Los posts de un autor, del más nuevo al más viejo. Es el camino de pull del híbrido (S3). */
    fun recentBy(authorId: UserId, limit: Int): List<PostId>
}

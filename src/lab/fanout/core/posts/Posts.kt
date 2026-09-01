package lab.fanout.core.posts

interface Posts {
    fun add(post: Post)
    fun get(id: PostId): Post
}

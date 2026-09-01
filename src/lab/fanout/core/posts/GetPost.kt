package lab.fanout.core.posts

import dev.botta.cqbus.identity.Identity
import dev.botta.cqbus.requests.Query
import dev.botta.cqbus.requests.handlers.RequestHandler

data class GetPost(val postId: PostId): Query<Post> {
    internal class Handler(private val posts: Posts): RequestHandler<GetPost, Post> {
        override fun execute(request: GetPost, identity: Identity): Post = posts.get(request.postId)
    }
}

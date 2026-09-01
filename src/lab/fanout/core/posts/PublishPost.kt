package lab.fanout.core.posts

import dev.botta.cqbus.identity.Identity
import dev.botta.cqbus.requests.Command
import dev.botta.cqbus.requests.handlers.RequestHandler
import lab.fanout.core.identity.UserId

data class PublishPost(val authorId: UserId, val text: String): Command<PublishPost.Result> {
    data class Result(val postId: PostId)

    internal class Handler(private val posts: Posts): RequestHandler<PublishPost, Result> {
        override fun execute(request: PublishPost, identity: Identity): Result {
            val post = Post(authorId = request.authorId, text = request.text)
            posts.add(post)
            return Result(post.id)
        }
    }
}

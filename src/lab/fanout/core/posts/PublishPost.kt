package lab.fanout.core.posts

import dev.botta.cqbus.identity.Identity
import dev.botta.cqbus.requests.Command
import dev.botta.cqbus.requests.handlers.RequestHandler
import dev.botta.trantor.core.jobs.JobDispatcher
import lab.fanout.core.identity.UserId
import lab.fanout.core.timelines.FanoutPost

data class PublishPost(val authorId: UserId, val text: String): Command<PublishPost.Result> {
    data class Result(val postId: PostId)

    internal class Handler(private val posts: Posts, private val jobs: JobDispatcher): RequestHandler<PublishPost, Result> {
        override fun execute(request: PublishPost, identity: Identity): Result {
            val post = Post(authorId = request.authorId, text = request.text)
            posts.add(post)
            // El fan-out no pasa por acá: publicar contesta apenas el post está guardado.
            jobs.dispatch(FanoutPost(post.id, post.authorId))
            return Result(post.id)
        }
    }
}

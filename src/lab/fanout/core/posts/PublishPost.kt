package lab.fanout.core.posts

import dev.botta.cqbus.identity.Identity
import dev.botta.cqbus.requests.Command
import dev.botta.cqbus.requests.handlers.RequestHandler
import dev.botta.trantor.core.tx.TransactionManager
import dev.botta.trantor.core.tx.transactional
import dev.botta.trantor.primitives.events.EventDispatcher
import lab.fanout.core.identity.UserId

data class PublishPost(val authorId: UserId, val text: String): Command<PublishPost.Result> {
    data class Result(val postId: PostId)

    internal class Handler(
        private val posts: Posts,
        private val cache: PostCache,
        private val events: EventDispatcher,
        private val tx: TransactionManager,
    ): RequestHandler<PublishPost, Result> {
        override fun execute(request: PublishPost, identity: Identity): Result {
            val post = Post(authorId = request.authorId, text = request.text)
            // El publish va primero: sin defer, el handler correría antes de persistir y el
            // cache estaría frío. `defer` lo bufferiza y dispara al salir del bloque.
            // La tx hace que `afterCommit` del handler encolado no corra si esto revierte.
            tx.transactional {
                events.defer {
                    events.publish(PostPublished(post.id, post.authorId))
                    posts.add(post)
                    cache.put(post)
                }
            }
            return Result(post.id)
        }
    }
}

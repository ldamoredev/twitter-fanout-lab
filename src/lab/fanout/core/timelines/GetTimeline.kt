package lab.fanout.core.timelines

import dev.botta.cqbus.identity.Identity
import dev.botta.cqbus.requests.Query
import dev.botta.cqbus.requests.handlers.RequestHandler
import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.PostId

data class GetTimeline(val userId: UserId): Query<GetTimeline.Feed> {
    data class Feed(val postIds: List<PostId>)

    internal class Handler(private val timelines: Timelines): RequestHandler<GetTimeline, Feed> {
        override fun execute(request: GetTimeline, identity: Identity) =
            Feed(timelines.idsOf(request.userId))
    }
}

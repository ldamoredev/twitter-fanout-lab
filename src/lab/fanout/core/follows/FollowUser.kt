package lab.fanout.core.follows

import dev.botta.cqbus.identity.Identity
import dev.botta.cqbus.requests.Command
import dev.botta.cqbus.requests.handlers.RequestHandler
import lab.fanout.core.identity.UserId

data class FollowUser(
    val followerId: UserId,
    val followeeId: UserId,
): Command<Unit> {
    internal class Handler(private val follows: Follows): RequestHandler<FollowUser, Unit> {
        override fun execute(request: FollowUser, identity: Identity) {
            follows.add(Follow(request.followerId, request.followeeId))
        }
    }
}

package lab.fanout.core.follows

import lab.fanout.core.identity.UserId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class InMemoryFollows: Follows {
    private val followeesByFollower = ConcurrentHashMap<UserId, CopyOnWriteArrayList<UserId>>()
    private val followersByFollowee = ConcurrentHashMap<UserId, CopyOnWriteArrayList<UserId>>()

    override fun add(follow: Follow) {
        followeesByFollower.getOrPut(follow.followerId) { CopyOnWriteArrayList() }.addIfAbsent(follow.followeeId)
        followersByFollowee.getOrPut(follow.followeeId) { CopyOnWriteArrayList() }.addIfAbsent(follow.followerId)
    }

    override fun followeesOf(followerId: UserId): List<UserId> =
        followeesByFollower[followerId]?.toList() ?: emptyList()

    override fun followersOf(followeeId: UserId): List<UserId> =
        followersByFollowee[followeeId]?.toList() ?: emptyList()
}

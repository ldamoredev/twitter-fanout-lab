package lab.fanout.core.follows

import lab.fanout.core.identity.UserId

interface Follows {
    fun add(follow: Follow)
    fun followeesOf(followerId: UserId): List<UserId>
    fun followersOf(followeeId: UserId): List<UserId>
}

package lab.fanout.core.follows

import lab.fanout.core.identity.UserId

class Follow(val followerId: UserId, val followeeId: UserId) {
    init {
        if (followerId == followeeId) throw CannotFollowYourself()
    }
}

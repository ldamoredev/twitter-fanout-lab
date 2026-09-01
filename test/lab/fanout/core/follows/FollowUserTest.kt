package lab.fanout.core.follows

import dev.botta.cqbus.CQBus
import lab.fanout.core.identity.UserId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class FollowUserTest {
    @Test
    fun `un follow queda registrado entre seguidor y seguido`() {
        val follows = InMemoryFollows()
        val bus = CQBus()
        bus.registerHandler { FollowUser.Handler(follows) }
        val alice = UserId()
        val bob = UserId()

        bus.execute(FollowUser(followerId = alice, followeeId = bob))

        assertThat(follows.followeesOf(alice)).containsExactly(bob)
        assertThat(follows.followersOf(bob)).containsExactly(alice)
    }

    @Test
    fun `un usuario no puede seguirse a si mismo`() {
        val me = UserId()
        assertThatThrownBy { Follow(followerId = me, followeeId = me) }
            .isInstanceOf(CannotFollowYourself::class.java)
    }
}

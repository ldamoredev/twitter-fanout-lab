package lab.fanout.core.timelines

import dev.botta.cqbus.CQBus
import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.InMemoryPosts
import lab.fanout.core.posts.PublishPost
import lab.fanout.doubles.RecordingJobDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GetTimelineTest {
    @Test
    fun `el timeline precomputado expone ids y no el texto del post`() {
        val posts = InMemoryPosts()
        val timelines = InMemoryTimelines()
        val bus = CQBus()
        bus.registerHandler { PublishPost.Handler(posts, RecordingJobDispatcher()) }
        bus.registerHandler { GetTimeline.Handler(timelines) }
        val alice = UserId()
        val bob = UserId()
        val published = bus.execute(PublishPost(bob, "este texto no viaja en el timeline"))
        timelines.prepend(alice, published.postId)

        val feed = bus.execute(GetTimeline(alice))

        assertThat(feed.postIds).containsExactly(published.postId)
        assertThat(feed).hasOnlyFields("postIds")
    }
}

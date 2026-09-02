package lab.fanout.core.timelines

import dev.botta.cqbus.CQBus
import dev.botta.trantor.primitives.events.on
import lab.fanout.core.follows.Follow
import lab.fanout.core.follows.InMemoryFollows
import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.InMemoryPosts
import lab.fanout.core.posts.PostId
import lab.fanout.core.posts.PostPublished
import lab.fanout.core.posts.PublishPost
import lab.fanout.doubles.RecordingEventDispatcher
import lab.fanout.doubles.RecordingJobDispatcher
import lab.fanout.doubles.testPostCache
import lab.fanout.platform.tx.InMemoryTransactionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FanoutOnWriteTest {
    @Test
    fun `publicar un post despacha un solo job, no uno por seguidor`() {
        val jobs = RecordingJobDispatcher()
        val events = RecordingEventDispatcher()
        events.on<PostPublished> { event ->
            jobs.dispatch(FanoutPost(event.postId, event.authorId))
        }
        val bus = CQBus()
        bus.registerHandler { PublishPost.Handler(InMemoryPosts(), testPostCache(), events, InMemoryTransactionManager()) }
        val authorId = UserId()

        val published = bus.execute(PublishPost(authorId, "hola lab"))

        assertThat(jobs.only<FanoutPost>()).containsExactly(FanoutPost(published.postId, authorId))
    }

    @Test
    fun `el fan-out reparte a los seguidores en chunks y cubre a todos`() {
        val follows = InMemoryFollows()
        val authorId = UserId()
        val followerIds = (1..250).map { UserId() }
        followerIds.forEach { follows.add(Follow(followerId = it, followeeId = authorId)) }
        val jobs = RecordingJobDispatcher()
        val postId = PostId()

        FanoutPost.Handler(follows, jobs, CelebrityThreshold()).execute(FanoutPost(postId, authorId))

        val chunks = jobs.only<WriteTimelineChunk>()
        assertThat(chunks).hasSize(3) // 250 seguidores / FANOUT_CHUNK_FOLLOWERS
        assertThat(chunks.map { it.followerIds.size }).containsExactly(100, 100, 50)
        assertThat(chunks.flatMap { it.followerIds }).containsExactlyInAnyOrderElementsOf(followerIds)
    }

    @Test
    fun `un autor sin seguidores no genera trabajo`() {
        val jobs = RecordingJobDispatcher()

        FanoutPost.Handler(InMemoryFollows(), jobs, CelebrityThreshold()).execute(FanoutPost(PostId(), UserId()))

        assertThat(jobs.dispatched).isEmpty()
    }

    @Test
    fun `escribir un chunk prepende el post en el timeline de cada seguidor`() {
        val timelines = InMemoryTimelines()
        val followerIds = (1..3).map { UserId() }
        val postId = PostId()

        WriteTimelineChunk.Handler(timelines).execute(WriteTimelineChunk(postId, followerIds))

        followerIds.forEach { assertThat(timelines.idsOf(it)).containsExactly(postId) }
    }
}

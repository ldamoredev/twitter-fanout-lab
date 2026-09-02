package lab.fanout.core.posts

import dev.botta.cqbus.CQBus
import dev.botta.trantor.primitives.events.on
import lab.fanout.core.identity.UserId
import lab.fanout.core.timelines.FanoutPost
import lab.fanout.doubles.RecordingEventDispatcher
import lab.fanout.doubles.RecordingJobDispatcher
import lab.fanout.doubles.testPostCache
import lab.fanout.platform.tx.InMemoryTransactionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PublishPostDeferTest {
    @Test
    fun `publicar usa defer para no disparar el fan-out a mitad de la escritura`() {
        val posts = InMemoryPosts()
        val cache = testPostCache()
        val events = RecordingEventDispatcher()
        val jobs = RecordingJobDispatcher()
        events.on<PostPublished> { event ->
            assertThat(posts.get(event.postId).text).isEqualTo("despues del defer")
            assertThat(cache.get(event.postId) { error("tenia que estar cacheado") }.text)
                .isEqualTo("despues del defer")
            jobs.dispatch(FanoutPost(event.postId, event.authorId))
        }
        val bus = CQBus()
        bus.registerHandler { PublishPost.Handler(posts, cache, events, InMemoryTransactionManager()) }

        val published = bus.execute(PublishPost(UserId(), "despues del defer"))

        assertThat(events.deferCalls).isEqualTo(1)
        assertThat(events.published.filterIsInstance<PostPublished>()).hasSize(1)
        assertThat(jobs.only<FanoutPost>().single().postId).isEqualTo(published.postId)
    }

    @Test
    fun `el evento de publicacion se dispara despues de persistir aunque se publique antes en el bloque`() {
        val posts = InMemoryPosts()
        val cache = testPostCache()
        val events = RecordingEventDispatcher()
        var elHandlerVioElPost = false
        events.on<PostPublished> { event ->
            elHandlerVioElPost = posts.get(event.postId).text == "ordenado por defer"
        }
        val bus = CQBus()
        bus.registerHandler { PublishPost.Handler(posts, cache, events, InMemoryTransactionManager()) }

        bus.execute(PublishPost(UserId(), "ordenado por defer"))

        assertThat(elHandlerVioElPost).isTrue()
        assertThat(events.deferCalls).isEqualTo(1)
    }
}

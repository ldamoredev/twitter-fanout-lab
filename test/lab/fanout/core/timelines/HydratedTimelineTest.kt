package lab.fanout.core.timelines

import dev.botta.cqbus.CQBus
import lab.fanout.core.follows.Follow
import lab.fanout.core.follows.InMemoryFollows
import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.InMemoryPosts
import lab.fanout.core.posts.Post
import lab.fanout.core.posts.PostCache
import lab.fanout.core.posts.PostId
import lab.fanout.core.posts.Posts
import lab.fanout.core.posts.PublishPost
import lab.fanout.doubles.RecordingEventDispatcher
import lab.fanout.doubles.testPostCache
import lab.fanout.platform.tx.InMemoryTransactionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HydratedTimelineTest {
    private val follows = InMemoryFollows()
    private val posts = InMemoryPosts()
    private val timelines = InMemoryTimelines()
    private val cache = testPostCache()
    private val events = RecordingEventDispatcher()

    @Test
    fun `el timeline hidrata los ids con el texto del post`() {
        val alice = UserId()
        val bob = UserId()
        val published = publicar(bob, "ahora el texto viaja")
        timelines.prepend(alice, published)

        val feed = feedDe(alice)

        assertThat(feed.posts).containsExactly(
            GetTimeline.TimelinePost(published, bob, "ahora el texto viaja"),
        )
    }

    @Test
    fun `hidratar usa el cache y no el store si el snapshot ya esta`() {
        val alice = UserId()
        val autor = UserId()
        val post = Post(authorId = autor, text = "vive en el cache")
        cache.put(post)
        timelines.prepend(alice, post.id)
        val storeMuerto = object: Posts {
            override fun add(post: Post) = error("no deberia escribir")
            override fun get(id: PostId) = error("no deberia ir al store")
            override fun recentBy(authorId: UserId, limit: Int) = emptyList<PostId>()
        }

        val feed = feedDe(alice, store = storeMuerto)

        assertThat(feed.posts.single().text).isEqualTo("vive en el cache")
    }

    @Test
    fun `si el cache no tiene el snapshot se hidrata desde el store`() {
        val alice = UserId()
        val bob = UserId()
        val post = Post(authorId = bob, text = "vive en el store")
        posts.add(post)
        timelines.prepend(alice, post.id)

        val feed = feedDe(alice, cache = testPostCache())

        assertThat(feed.posts.single().text).isEqualTo("vive en el store")
    }

    @Test
    fun `el autor ve su post en el timeline sin esperar el fan-out`() {
        val autor = UserId()
        val published = publicar(autor, "lo mio ya")

        val feed = feedDe(autor)

        assertThat(feed.posts.single().postId).isEqualTo(published)
        assertThat(feed.posts.single().text).isEqualTo("lo mio ya")
        assertThat(timelines.idsOf(autor)).isEmpty()
    }

    @Test
    fun `el seguidor no ve el post hasta que el fan-out lo escribe`() {
        val autor = UserId()
        val seguidor = UserId()
        follows.add(Follow(followerId = seguidor, followeeId = autor))
        val published = publicar(autor, "todavia no")

        assertThat(feedDe(seguidor).posts).isEmpty()

        timelines.prepend(seguidor, published)

        assertThat(feedDe(seguidor).posts.single().text).isEqualTo("todavia no")
    }

    @Test
    fun `el post del autor no se duplica si tambien esta en el timeline precomputado`() {
        val autor = UserId()
        val published = publicar(autor, "una sola vez")
        timelines.prepend(autor, published)

        assertThat(feedDe(autor).posts.map { it.postId }).containsExactly(published)
    }

    private fun publicar(authorId: UserId, text: String): PostId {
        val bus = CQBus()
        bus.registerHandler { PublishPost.Handler(posts, cache, events, InMemoryTransactionManager()) }
        return bus.execute(PublishPost(authorId, text)).postId
    }

    private fun feedDe(
        userId: UserId,
        store: Posts = posts,
        cache: PostCache = this.cache,
    ): GetTimeline.Feed {
        val bus = CQBus()
        bus.registerHandler { GetTimeline.Handler(timelines, follows, store, CelebrityThreshold(), cache) }
        return bus.execute(GetTimeline(userId))
    }
}

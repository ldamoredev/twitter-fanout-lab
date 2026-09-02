package lab.fanout.core.timelines

import dev.botta.cqbus.CQBus
import lab.fanout.core.follows.Follow
import lab.fanout.core.follows.InMemoryFollows
import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.InMemoryPosts
import lab.fanout.core.posts.Post
import lab.fanout.core.posts.PostId
import lab.fanout.doubles.RecordingJobDispatcher
import lab.fanout.doubles.testPostCache
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HybridTimelineTest {
    private val umbralDe50 = CelebrityThreshold(50)
    private val follows = InMemoryFollows()
    private val posts = InMemoryPosts()
    private val timelines = InMemoryTimelines()

    @Test
    fun `un autor con menos seguidores que el umbral dispara fan-out`() {
        val authorId = autorCon(seguidores = 49)
        val jobs = RecordingJobDispatcher()

        FanoutPost.Handler(follows, jobs, umbralDe50).execute(FanoutPost(PostId(), authorId))

        assertThat(jobs.only<WriteTimelineChunk>().flatMap { it.followerIds }).hasSize(49)
    }

    @Test
    fun `un autor con mas seguidores que el umbral no dispara fan-out`() {
        val authorId = autorCon(seguidores = 51)
        val jobs = RecordingJobDispatcher()

        FanoutPost.Handler(follows, jobs, umbralDe50).execute(FanoutPost(PostId(), authorId))

        assertThat(jobs.dispatched).isEmpty()
    }

    @Test
    fun `un autor con exactamente el umbral todavia dispara fan-out`() {
        val authorId = autorCon(seguidores = 50)
        val jobs = RecordingJobDispatcher()

        FanoutPost.Handler(follows, jobs, umbralDe50).execute(FanoutPost(PostId(), authorId))

        assertThat(jobs.only<WriteTimelineChunk>().flatMap { it.followerIds }).hasSize(50)
    }

    @Test
    fun `el feed mergea el timeline precomputado con los posts recientes de las celebridades`() {
        val lector = UserId()
        val celebridad = autorCon(seguidores = 51)
        val normales = (1..10).map { autorCon(seguidores = 3) }
        (normales + celebridad).forEach { follows.add(Follow(followerId = lector, followeeId = it)) }
        val deNormales = normales.map { publica(it, "post normal", conFanoutA = lector) }
        val deCelebridad = publica(celebridad, "post de celebridad")

        val feed = feedDe(lector)

        assertThat(idsDe(feed)).hasSize(11)
        assertThat(idsDe(feed)).containsAll(deNormales)
        assertThat(idsDe(feed)).contains(deCelebridad)
    }

    @Test
    fun `el feed devuelve los posts del mas nuevo al mas viejo sin importar de donde vengan`() {
        val lector = UserId()
        val celebridad = autorCon(seguidores = 51)
        val normal = autorCon(seguidores = 3)
        listOf(celebridad, normal).forEach { follows.add(Follow(followerId = lector, followeeId = it)) }
        val viejoNormal = publica(normal, "1", conFanoutA = lector)
        val viejoCelebridad = publica(celebridad, "2")
        val nuevoNormal = publica(normal, "3", conFanoutA = lector)
        val nuevoCelebridad = publica(celebridad, "4")

        val feed = feedDe(lector)

        assertThat(idsDe(feed)).containsExactly(nuevoCelebridad, nuevoNormal, viejoCelebridad, viejoNormal)
    }

    @Test
    fun `al leer no se pullean los posts de los que no son celebridades`() {
        val lector = UserId()
        val normal = autorCon(seguidores = 3)
        follows.add(Follow(followerId = lector, followeeId = normal))
        // El post existe pero nadie lo prepende: si apareciera, el lector estaria leyendo por pull.
        publica(normal, "este post no se pullea")

        assertThat(idsDe(feedDe(lector))).isEmpty()
    }

    @Test
    fun `un post que esta en los dos lados aparece una sola vez`() {
        val lector = UserId()
        val celebridad = autorCon(seguidores = 51)
        follows.add(Follow(followerId = lector, followeeId = celebridad))
        // Cruzar el umbral despues de publicar deja el post escrito y ademas pulleable.
        val post = publica(celebridad, "publicado justo en el borde", conFanoutA = lector)

        assertThat(idsDe(feedDe(lector))).containsExactly(post)
    }

    private fun autorCon(seguidores: Int): UserId {
        val authorId = UserId()
        repeat(seguidores) { follows.add(Follow(followerId = UserId(), followeeId = authorId)) }
        return authorId
    }

    private fun publica(authorId: UserId, text: String, conFanoutA: UserId? = null): PostId {
        val post = Post(authorId = authorId, text = text)
        posts.add(post)
        conFanoutA?.let { timelines.prepend(it, post.id) }
        return post.id
    }

    private fun feedDe(userId: UserId): GetTimeline.Feed {
        val bus = CQBus()
        bus.registerHandler { GetTimeline.Handler(timelines, follows, posts, umbralDe50, testPostCache()) }
        return bus.execute(GetTimeline(userId))
    }

    private fun idsDe(feed: GetTimeline.Feed) = feed.posts.map { it.postId }
}

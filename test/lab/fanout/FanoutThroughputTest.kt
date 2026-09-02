package lab.fanout

import com.google.gson.JsonParser
import dev.botta.trantor.config.providers.addMemoryCollection
import dev.botta.trantor.hosting.addModule
import dev.botta.trantor.web.application.WebApplication
import lab.fanout.core.follows.Follow
import lab.fanout.core.follows.Follows
import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.PostId
import lab.fanout.core.timelines.FANOUT_CHUNK_FOLLOWERS
import lab.fanout.core.timelines.Timelines
import lab.fanout.platform.queues.InMemoryMessageQueue
import lab.fanout.web.TwitterFanoutWebModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS

/** El número que pide el slice: cuánto tarda el fan-out de un post con 1.000 seguidores. */
private const val FOLLOWERS = 1_000

/** Outbox + 1 job que reparte + 1 por cada tanda de seguidores. */
private const val EXPECTED_JOBS = 2L + FOLLOWERS / FANOUT_CHUNK_FOLLOWERS

/** Techo de cordura, no la medición: en el proceso el fan-out tarda decenas de milisegundos. */
private const val FANOUT_BUDGET_MILLIS = 5_000L

class FanoutThroughputTest {
    @Test
    fun `un post con mil seguidores se reparte en doce jobs y llega a todos los timelines`() {
        withApp { port, app ->
            val authorId = UserId()
            val followerIds = (1..FOLLOWERS).map { UserId() }
            val follows = app.services.get<Follows>()
            followerIds.forEach { follows.add(Follow(followerId = it, followeeId = authorId)) }
            val timelines = app.services.get<Timelines>()
            val queue = app.services.get<InMemoryMessageQueue>()

            val startedAt = System.nanoTime()
            val created = post(port, "/posts", """{"authorId":"$authorId","text":"hola lab"}""")
            val publishMillis = millisSince(startedAt)
            assertThat(created.statusCode()).isEqualTo(201)
            val postId = PostId(UUID.fromString(JsonParser.parseString(created.body()).asJsonObject["postId"].asString))

            val fanoutMillis = await("los ${FOLLOWERS} timelines tienen el post") {
                followerIds.all { timelines.idsOf(it).contains(postId) }
            }
            await("la cola quedó vacía") { queue.metrics().deleted == EXPECTED_JOBS }

            val metrics = queue.metrics()
            assertThat(metrics.enqueued).isEqualTo(EXPECTED_JOBS)
            assertThat(metrics.delivered).isEqualTo(EXPECTED_JOBS) // ningún reintento
            assertThat(metrics.pending).isZero()
            assertThat(fanoutMillis).isLessThan(FANOUT_BUDGET_MILLIS)

            val exposed = get(port, "/metrics/fanout")
            assertThat(exposed.statusCode()).isEqualTo(200)
            assertThat(exposed.body()).contains("\"jobsEnqueued\":$EXPECTED_JOBS")
            assertThat(exposed.body()).contains("\"jobsProcessed\":$EXPECTED_JOBS")
            assertThat(exposed.body()).contains("\"jobsPending\":0")
            println(
                "S2 fan-out · $FOLLOWERS seguidores · ${metrics.enqueued} jobs " +
                    "· publish respondió en $publishMillis ms · fan-out completo en $fanoutMillis ms"
            )
        }
    }

    @Test
    fun `publicar contesta sin esperar a que el fan-out termine`() {
        withApp { port, app ->
            val authorId = UserId()
            val follows = app.services.get<Follows>()
            repeat(FOLLOWERS) { follows.add(Follow(followerId = UserId(), followeeId = authorId)) }

            val created = post(port, "/posts", """{"authorId":"$authorId","text":"hola lab"}""")

            assertThat(created.statusCode()).isEqualTo(201)
            // El request devuelve el id del post; escribir 1.000 timelines es trabajo de la cola.
            assertThat(JsonParser.parseString(created.body()).asJsonObject["postId"].asString).isNotBlank()
        }
    }

    private fun await(what: String, condition: () -> Boolean): Long {
        val startedAt = System.nanoTime()
        while (millisSince(startedAt) < FANOUT_BUDGET_MILLIS) {
            if (condition()) return millisSince(startedAt)
            Thread.sleep(5)
        }
        throw AssertionError("Timeout de ${FANOUT_BUDGET_MILLIS} ms esperando: $what")
    }

    private fun millisSince(startedAtNanos: Long) = MILLISECONDS.convert(System.nanoTime() - startedAtNanos, NANOSECONDS)

    private fun withApp(body: (Int, WebApplication) -> Unit) {
        val port = ServerSocket(0).use { it.localPort }
        val builder = WebApplication.builder {
            appName = "twitter-fanout-lab"
            environmentName = "TEST"
        }
        builder.addModule<TwitterFanoutWebModule>()
        builder.config.addMemoryCollection(mapOf("httpServer.port" to port.toString()))
        val app = builder.build()
        app.start()
        try {
            body(port, app)
        } finally {
            app.stop()
        }
    }

    private fun get(port: Int, path: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun post(port: Int, path: String, json: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
}

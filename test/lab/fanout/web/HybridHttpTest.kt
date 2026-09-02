package lab.fanout.web

import com.google.gson.JsonParser
import dev.botta.trantor.config.providers.addMemoryCollection
import dev.botta.trantor.hosting.addModule
import dev.botta.trantor.web.application.WebApplication
import lab.fanout.core.identity.UserId
import lab.fanout.platform.queues.InMemoryMessageQueue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Bajo por config lo que en producción son 10.000, para que tres seguidores ya sean celebridad. */
private const val THRESHOLD_FOLLOWERS = 2

private const val DRAIN_BUDGET_MILLIS = 5_000L

class HybridHttpTest {
    @Test
    fun `el post de una celebridad llega al feed sin que nadie le haya escrito el timeline`() {
        withApp { port, app ->
            val lector = UserId()
            val celebridad = UserId()
            val normal = UserId()
            // La celebridad pasa el umbral; el normal no.
            listOf(lector, UserId(), UserId()).forEach { follow(port, it, celebridad) }
            follow(port, lector, normal)

            val delNormal = publish(port, normal, "post con fan-out")
            val deCelebridad = publish(port, celebridad, "post sin fan-out")

            val queue = app.services.get<InMemoryMessageQueue>()
            // 2 jobs del normal (reparto + un chunk) y 1 de la celebridad (reparto que no despacha).
            await("la cola drenó") { queue.metrics().deleted == 3L && queue.metrics().pending == 0 }

            val feed = get(port, "/timelines/$lector")
            assertThat(feed.statusCode()).isEqualTo(200)
            assertThat(postIdsOf(feed)).containsExactly(deCelebridad, delNormal)
            assertThat(feed.body()).contains("post sin fan-out")
            assertThat(feed.body()).contains("post con fan-out")
        }
    }

    @Test
    fun `el umbral se baja por configuracion sin recompilar`() {
        withApp { port, app ->
            val autor = UserId()
            // Tres seguidores: por debajo del default de 10.000, por encima del umbral configurado.
            repeat(3) { follow(port, UserId(), autor) }

            publish(port, autor, "no deberia disparar chunks")

            val queue = app.services.get<InMemoryMessageQueue>()
            await("la cola drenó") { queue.metrics().pending == 0 }
            assertThat(queue.metrics().enqueued).isEqualTo(1L) // sólo el FanoutPost, ningún chunk
        }
    }

    private fun postIdsOf(response: HttpResponse<String>) =
        JsonParser.parseString(response.body()).asJsonObject["posts"].asJsonArray.map {
            it.asJsonObject["postId"].asString
        }

    private fun publish(port: Int, authorId: UserId, text: String): String {
        val created = post(port, "/posts", """{"authorId":"$authorId","text":"$text"}""")
        assertThat(created.statusCode()).isEqualTo(201)
        return JsonParser.parseString(created.body()).asJsonObject["postId"].asString
    }

    private fun follow(port: Int, followerId: UserId, followeeId: UserId) {
        val response = post(port, "/follows", """{"followerId":"$followerId","followeeId":"$followeeId"}""")
        assertThat(response.statusCode()).isBetween(200, 299)
    }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + DRAIN_BUDGET_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("Timeout de $DRAIN_BUDGET_MILLIS ms esperando: $what")
    }

    private fun withApp(body: (Int, WebApplication) -> Unit) {
        val port = ServerSocket(0).use { it.localPort }
        val builder = WebApplication.builder {
            appName = "twitter-fanout-lab"
            environmentName = "TEST"
        }
        builder.addModule<TwitterFanoutWebModule>()
        builder.config.addMemoryCollection(
            mapOf(
                "httpServer.port" to port.toString(),
                "fanout.celebrityThresholdFollowers" to THRESHOLD_FOLLOWERS.toString(),
            )
        )
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

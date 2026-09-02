package lab.fanout.web

import com.google.gson.JsonParser
import dev.botta.trantor.config.providers.addMemoryCollection
import dev.botta.trantor.hosting.addModule
import dev.botta.trantor.web.application.WebApplication
import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.PostId
import lab.fanout.core.timelines.Timelines
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

class PostsHttpTest {
    @Test
    fun `un post publicado por http se lee por id`() {
        withApp { port ->
            val authorId = UserId().toString()
            val created = post(
                port,
                "/posts",
                """{"authorId":"$authorId","text":"hola lab"}""",
            )
            assertThat(created.statusCode()).isEqualTo(201)
            val postId = JsonParser.parseString(created.body()).asJsonObject.get("postId").asString

            val loaded = get(port, "/posts/$postId")
            assertThat(loaded.statusCode()).isEqualTo(200)
            assertThat(loaded.body()).contains("\"id\":\"$postId\"")
            assertThat(loaded.body()).contains("\"authorId\":\"$authorId\"")
            assertThat(loaded.body()).contains("\"text\":\"hola lab\"")
        }
    }

    @Test
    fun `un post que no existe responde 404`() {
        withApp { port ->
            val missing = PostId().toString()
            val response = get(port, "/posts/$missing")

            assertThat(response.statusCode()).isEqualTo(404)
            assertThat(response.body()).contains("NotFoundError")
        }
    }

    @Test
    fun `el timeline por http hidrata el texto del post`() {
        withApp { port, app ->
            val alice = UserId()
            val created = post(
                port,
                "/posts",
                """{"authorId":"${UserId()}","text":"este texto ahora viaja en el timeline"}""",
            )
            val postId = JsonParser.parseString(created.body()).asJsonObject.get("postId").asString
            app.services.get<Timelines>().prepend(alice, PostId(UUID.fromString(postId)))

            val feed = get(port, "/timelines/$alice")
            assertThat(feed.statusCode()).isEqualTo(200)
            assertThat(feed.body()).contains("\"posts\"")
            assertThat(feed.body()).contains(postId)
            assertThat(feed.body()).contains("este texto ahora viaja en el timeline")
            assertThat(feed.body()).doesNotContain("\"postIds\"")
        }
    }

    @Test
    fun `el autor lee su post hidratado sin esperar el fan-out`() {
        withApp { port ->
            val autor = UserId()
            val created = post(
                port,
                "/posts",
                """{"authorId":"$autor","text":"lo mio ya"}""",
            )
            val postId = JsonParser.parseString(created.body()).asJsonObject.get("postId").asString

            val feed = get(port, "/timelines/$autor")
            assertThat(feed.statusCode()).isEqualTo(200)
            assertThat(feed.body()).contains(postId)
            assertThat(feed.body()).contains("lo mio ya")
        }
    }

    private fun withApp(body: (Int) -> Unit) = withApp { port, _ -> body(port) }

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

    private fun post(port: Int, path: String, json: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun get(port: Int, path: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
}

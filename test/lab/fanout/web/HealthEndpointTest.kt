package lab.fanout.web

import dev.botta.trantor.config.providers.addMemoryCollection
import dev.botta.trantor.hosting.addModule
import dev.botta.trantor.web.application.WebApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class HealthEndpointTest {
    @Test
    fun `el endpoint de salud responde 200`() {
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
            val response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            assertThat(response.statusCode()).isEqualTo(200)
            assertThat(response.body()).contains("ok")
        } finally {
            app.stop()
        }
    }
}

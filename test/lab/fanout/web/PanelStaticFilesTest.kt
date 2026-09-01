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

class PanelStaticFilesTest {
    @Test
    fun `la portada del panel se sirve en la raiz`() {
        withApp { port ->
            val response = get(port, "/")
            assertThat(response.statusCode()).isEqualTo(200)
            assertThat(response.body()).contains("¿Cómo diseñarías el timeline de Twitter?")
            assertThat(response.body()).contains("panel.css")
        }
    }

    @Test
    fun `la pagina del modelo trae la calculadora con las constantes de TimelineStorage`() {
        withApp { port ->
            val response = get(port, "/modelo.html")
            assertThat(response.statusCode()).isEqualTo(200)
            assertThat(response.body()).contains("value=\"50000000\"")
            assertThat(response.body()).contains("value=\"800\"")
            assertThat(response.body()).contains("value=\"16\"")
            assertThat(response.body()).contains("value=\"320\"")
            assertThat(response.body()).contains("publicar un post")
            assertThat(response.body()).contains("seguir a alguien")
            assertThat(response.body()).contains("pedir un timeline")
            assertThat(get(port, "/modelo.js").statusCode()).isEqualTo(200)
        }
    }

    @Test
    fun `el css y el modulo compartido del panel salen del classpath`() {
        withApp { port ->
            val css = get(port, "/panel.css")
            val js = get(port, "/lib.js")
            assertThat(css.statusCode()).isEqualTo(200)
            assertThat(css.body()).contains("--bg")
            assertThat(js.statusCode()).isEqualTo(200)
            assertThat(js.body()).contains("export async function call")
            assertThat(js.body()).contains("S6")
        }
    }

    private fun withApp(body: (Int) -> Unit) {
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
            body(port)
        } finally {
            app.stop()
        }
    }

    private fun get(port: Int, path: String): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
}

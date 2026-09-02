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
            val page = loadPage(port, "/")
            assertThat(page.html).contains("id=\"root\"")
            assertThat(page.js).contains("¿Cómo diseñarías el timeline de Twitter?")
        }
    }

    @Test
    fun `la pagina del modelo trae la calculadora con las constantes de TimelineStorage`() {
        withApp { port ->
            val page = loadPage(port, "/modelo.html")
            assertThat(page.js).contains("5e7")
            assertThat(page.js).contains("800")
            assertThat(page.js).contains("320")
            assertThat(page.js).contains("publicar un post")
            assertThat(page.js).contains("seguir a alguien")
            assertThat(page.js).contains("pedir un timeline")
        }
    }

    @Test
    fun `la pagina del fan-out cuenta la cadena de jobs y las constantes de FanoutTuning`() {
        withApp { port ->
            val page = loadPage(port, "/fanout.html")
            assertThat(page.js).contains("FanoutPost")
            assertThat(page.js).contains("WriteTimelineChunk")
            assertThat(page.js).contains("100") // FANOUT_CHUNK_FOLLOWERS
            assertThat(page.js).contains("/metrics/fanout")
        }
    }

    @Test
    fun `la pagina del hibrido trae el umbral de celebridad y los dos caminos`() {
        withApp { port ->
            val page = loadPage(port, "/hibrido.html")
            // esbuild pliega el literal a notación científica, igual que el 5e7 del modelo.
            assertThat(page.js).contains("1e4") // CELEBRITY_THRESHOLD_FOLLOWERS
            assertThat(page.js).contains("50") // CELEBRITY_MERGE_POSTS
            assertThat(page.js).contains("UUIDv7")
            assertThat(page.js).contains("CelebrityThreshold")
        }
    }

    @Test
    fun `el css del panel sale del classpath junto al bundle`() {
        withApp { port ->
            val page = loadPage(port, "/")
            assertThat(page.html).contains("/assets/")
            assertThat(page.css).contains("--bg")
        }
    }

    private data class Page(val html: String, val js: String, val css: String)

    private fun loadPage(port: Int, path: String): Page {
        val htmlResponse = get(port, path)
        assertThat(htmlResponse.statusCode()).isEqualTo(200)
        val html = htmlResponse.body()
        val jsHrefs = ASSET_JS.findAll(html).map { it.groupValues[1] }.distinct().toList()
        val cssHrefs = STYLESHEET.findAll(html).map { it.groupValues[1] }.distinct().toList()
        assertThat(jsHrefs).isNotEmpty()
        assertThat(cssHrefs).isNotEmpty()
        val js = jsHrefs.joinToString("\n") { href ->
            val response = get(port, href)
            assertThat(response.statusCode()).describedAs(href).isEqualTo(200)
            response.body()
        }
        val css = cssHrefs.joinToString("\n") { href ->
            val response = get(port, href)
            assertThat(response.statusCode()).describedAs(href).isEqualTo(200)
            response.body()
        }
        return Page(html = html, js = js, css = css)
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

    companion object {
        private val ASSET_JS = Regex("""(?:src|href)="(/assets/[^"]+\.js)"""")
        private val STYLESHEET = Regex("""<link[^>]+href="([^"]+\.css[^"]*)"""")
    }
}

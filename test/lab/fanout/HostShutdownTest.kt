package lab.fanout

import dev.botta.trantor.config.providers.addMemoryCollection
import dev.botta.trantor.hosting.HostedService
import dev.botta.trantor.hosting.addHostedService
import dev.botta.trantor.web.application.WebApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HostShutdownTest {
    @Test
    fun `el host para los hosted services al apagar`() {
        val probe = RecordingHostedService()
        val app = startHost(probe)
        assertThat(probe.started).isTrue()

        app.stop()

        assertThat(probe.stopped).isTrue()
        assertThat(probe.stopTimeoutSeconds).isEqualTo(30)
    }

    @Test
    fun `SIGTERM via stopApplication desbloquea run y para los servicios`() {
        val probe = RecordingHostedService()
        val app = buildHost(probe)
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        app.lifetime.onStarted { started.countDown() }
        val runner = Thread({
            app.run()
            finished.countDown()
        }, "host-run")
        runner.start()

        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue()
        app.lifetime.stopApplication()

        assertThat(finished.await(10, TimeUnit.SECONDS)).isTrue()
        assertThat(probe.started).isTrue()
        assertThat(probe.stopped).isTrue()
    }

    private fun startHost(probe: HostedService): WebApplication {
        val app = buildHost(probe)
        app.start()
        return app
    }

    private fun buildHost(probe: HostedService): WebApplication {
        val builder = WebApplication.builder {
            appName = "twitter-fanout-lab"
            environmentName = "TEST"
        }
        builder.config.addMemoryCollection(
            mapOf("httpServer.port" to freeTcpPort().toString())
        )
        builder.services.addHostedService(probe)
        return builder.build()
    }

    private fun freeTcpPort(): Int = ServerSocket(0).use { it.localPort }
}

class RecordingHostedService: HostedService {
    @Volatile var started = false
    @Volatile var stopped = false
    @Volatile var stopTimeoutSeconds: Int? = null

    override val name: String = "RecordingHostedService"

    override fun start() {
        started = true
    }

    override fun stop(timeoutSeconds: Int) {
        stopTimeoutSeconds = timeoutSeconds
        stopped = true
    }
}

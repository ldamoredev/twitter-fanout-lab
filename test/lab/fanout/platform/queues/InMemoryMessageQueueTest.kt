package lab.fanout.platform.queues

import dev.botta.trantor.core.queues.Message
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class InMemoryMessageQueueTest {
    @Test
    fun `un mensaje encolado se entrega una sola vez si el consumidor lo borra`() {
        val queue = InMemoryMessageQueue("fanout")

        queue.enqueue(Message("Job", "{}"))
        val delivered = queue.poll()
        delivered.forEach { queue.delete(it) }

        assertThat(delivered).hasSize(1)
        assertThat(queue.poll()).isEmpty()
        assertThat(queue.size()).isZero()
    }

    @Test
    fun `un mensaje que nadie borra vuelve a entregarse pasado el visibility timeout`() {
        val queue = InMemoryMessageQueue("fanout", visibilityTimeoutMillis = 40)

        queue.enqueue(Message("Job", "{}"))
        val first = queue.poll().single()
        Thread.sleep(80)
        val second = queue.poll().single()

        assertThat(second.id).isEqualTo(first.id)
        assertThat(second.attempts).isEqualTo(2)
    }

    @Test
    fun `poll espera en vez de devolver vacio en el acto`() {
        val queue = InMemoryMessageQueue("fanout", pollWaitMillis = 60)

        val elapsed = measureTimeMillis { assertThat(queue.poll()).isEmpty() }

        assertThat(elapsed).isGreaterThanOrEqualTo(60)
    }

    @Test
    fun `la cola cuenta lo encolado y lo procesado`() {
        val queue = InMemoryMessageQueue("fanout")

        queue.enqueue(Message("Job", "{}"))
        queue.enqueue(Message("Job", "{}"))
        queue.poll().take(1).forEach { queue.delete(it) }

        val metrics = queue.metrics()
        assertThat(metrics.enqueued).isEqualTo(2)
        assertThat(metrics.deleted).isEqualTo(1)
    }
}

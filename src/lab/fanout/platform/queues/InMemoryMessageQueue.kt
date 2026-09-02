package lab.fanout.platform.queues

import dev.botta.trantor.core.queues.EnqueueOptions
import dev.botta.trantor.core.queues.Message
import dev.botta.trantor.core.queues.MessageQueue
import dev.botta.trantor.core.queues.ReceivedMessage
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.DelayQueue
import java.util.concurrent.Delayed
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.concurrent.atomic.AtomicLong

/** SQS espera hasta 20 s por lote; el lab corre en un proceso, así que espera de a poco. */
private const val DEFAULT_POLL_WAIT_MILLIS = 200L

/** Cuánto tiene el worker para borrar el mensaje antes de que se vuelva a entregar. */
private const val DEFAULT_VISIBILITY_TIMEOUT_MILLIS = 30_000L

private const val MAX_MESSAGES_PER_POLL = 10

/**
 * La única `MessageQueue` publicada por Trantor es SQS. Esta es la del lab: misma semántica
 * (entrega al menos una vez, visibility timeout, borrado explícito) sin salir del proceso.
 *
 * `poll()` bloquea a propósito: el poller de `MessageQueueProcessor` hace `while (running)` sin
 * dormir entre vueltas, así que una cola que contesta vacío al instante le quema un core.
 */
class InMemoryMessageQueue(
    override val name: String,
    private val pollWaitMillis: Long = DEFAULT_POLL_WAIT_MILLIS,
    private val visibilityTimeoutMillis: Long = DEFAULT_VISIBILITY_TIMEOUT_MILLIS,
): MessageQueue {
    private val pending = DelayQueue<Envelope>()
    private val inFlight = ConcurrentHashMap<String, Envelope>()
    private val enqueued = AtomicLong()
    private val delivered = AtomicLong()
    private val deleted = AtomicLong()

    init {
        require(pollWaitMillis > 0) { "pollWaitMillis debe ser positivo, no $pollWaitMillis" }
        require(visibilityTimeoutMillis > 0) { "visibilityTimeoutMillis debe ser positivo, no $visibilityTimeoutMillis" }
    }

    override fun enqueue(message: Message, options: EnqueueOptions) {
        pending.put(Envelope(message, visibleIn(MILLISECONDS.convert(options.delaySeconds.toLong(), TimeUnit.SECONDS))))
        enqueued.incrementAndGet()
    }

    override fun poll(): List<ReceivedMessage> {
        val first = pending.poll(pollWaitMillis, MILLISECONDS) ?: return emptyList()
        val batch = mutableListOf(first)
        pending.drainTo(batch, MAX_MESSAGES_PER_POLL - 1)
        return batch.onEach { checkout(it) }
    }

    override fun delete(message: ReceivedMessage) {
        val envelope = inFlight.remove(message.id) ?: return
        pending.remove(envelope)
        deleted.incrementAndGet()
    }

    override fun size() = (pending.size - inFlight.size).coerceAtLeast(0)

    override fun clear() {
        pending.clear()
        inFlight.clear()
    }

    fun metrics() = QueueMetrics(
        name = name,
        enqueued = enqueued.get(),
        delivered = delivered.get(),
        deleted = deleted.get(),
        inFlight = inFlight.size,
        pending = size(),
    )

    /** Lo devuelve a la cola con el visibility timeout: si nadie lo borra, se reintenta. */
    private fun checkout(envelope: Envelope) {
        envelope.attempts++
        envelope.visibleAtNanos = visibleIn(visibilityTimeoutMillis)
        inFlight[envelope.id] = envelope
        pending.put(envelope)
        delivered.incrementAndGet()
    }

    private fun visibleIn(millis: Long) = System.nanoTime() + NANOSECONDS.convert(millis, MILLISECONDS)

    private class Envelope(override val message: Message, @Volatile var visibleAtNanos: Long): ReceivedMessage, Delayed {
        override val id: String = UUID.randomUUID().toString()
        override var attempts = 0

        override fun getDelay(unit: TimeUnit) = unit.convert(visibleAtNanos - System.nanoTime(), NANOSECONDS)

        override fun compareTo(other: Delayed) = getDelay(NANOSECONDS).compareTo(other.getDelay(NANOSECONDS))
    }
}

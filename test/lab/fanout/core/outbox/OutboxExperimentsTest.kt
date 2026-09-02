package lab.fanout.core.outbox

import dev.botta.trantor.core.events.DefaultEventDispatcher
import dev.botta.trantor.core.events.ProcessEventHandlerJob
import dev.botta.trantor.core.events.serialization.DefaultEventSerializer
import dev.botta.trantor.core.jobs.DefaultJobDispatcher
import dev.botta.trantor.core.jobs.Job
import dev.botta.trantor.core.jobs.JobHandler
import dev.botta.trantor.core.jobs.JobHandlerRegistry
import dev.botta.trantor.core.jobs.JobProcessor
import dev.botta.trantor.core.jobs.JobQueueRegistry
import dev.botta.trantor.core.jobs.registerHandler
import dev.botta.trantor.core.jobs.serialization.DefaultJobSerializer
import dev.botta.trantor.core.queues.EnqueueOptions
import dev.botta.trantor.core.queues.Message
import dev.botta.trantor.core.tx.transactional
import dev.botta.trantor.primitives.events.Event
import dev.botta.trantor.primitives.events.QueuedEventConfig
import dev.botta.trantor.primitives.events.QueuedEventHandler
import dev.botta.trantor.serialization.gson.GsonSerializer
import lab.fanout.core.identity.UserId
import lab.fanout.core.posts.PostId
import lab.fanout.core.posts.PostPublished
import lab.fanout.core.timelines.FANOUT_QUEUE_NAME
import lab.fanout.core.timelines.FanoutPost
import lab.fanout.doubles.RecordingJobDispatcher
import lab.fanout.platform.queues.InMemoryMessageQueue
import lab.fanout.platform.tx.InMemoryTransactionManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

class OutboxExperimentsTest {
    @Test
    fun `rollback despues de publicar el evento no encola el handler`() {
        val tm = InMemoryTransactionManager()
        val jobs = RecordingJobDispatcher()
        val events = DefaultEventDispatcher(tm, jobs, DefaultEventSerializer(GsonSerializer()))
        events.subscribe(FanoutOnPostPublished(jobs))

        try {
            tm.transactional {
                events.publish(PostPublished(PostId(), UserId()))
                error("rollback a propósito")
            }
        } catch (e: IllegalStateException) {
            assertThat(e.message).isEqualTo("rollback a propósito")
        }

        assertThat(jobs.dispatched).isEmpty()
    }

    @Test
    fun `el commit encola el handler y recien ahi dispara el fan-out`() {
        val tm = InMemoryTransactionManager()
        val jobs = RecordingJobDispatcher()
        val events = DefaultEventDispatcher(tm, jobs, DefaultEventSerializer(GsonSerializer()))
        events.subscribe(FanoutOnPostPublished(jobs))

        tm.transactional {
            events.publish(PostPublished(PostId(), UserId()))
            assertThat(jobs.dispatched).isEmpty()
        }

        assertThat(jobs.only<ProcessEventHandlerJob>()).hasSize(1)
        assertThat(jobs.only<FanoutPost>()).isEmpty()
    }

    @Test
    fun `el mismo deduplicationId se procesa dos veces porque la cola del lab lo ignora`() {
        val queue = InMemoryMessageQueue("exp", pollWaitMillis = 20)
        val options = EnqueueOptions(deduplicationId = "el-mismo-post")

        queue.enqueue(Message("FanoutPost", "{}"), options)
        queue.enqueue(Message("FanoutPost", "{}"), options)

        assertThat(queue.poll()).hasSize(2)
        assertThat(QueuedEventConfig::class.memberProperties.map { it.name })
            .doesNotContain("deduplicationId")
    }

    @Test
    fun `un event handler encolado que tira no reintenta, un Job comun si`() {
        val queue = InMemoryMessageQueue(
            FANOUT_QUEUE_NAME,
            pollWaitMillis = 20,
            visibilityTimeoutMillis = 80,
        )
        val tm = InMemoryTransactionManager()
        val handlerRegistry = JobHandlerRegistry()
        val jobSerializer = DefaultJobSerializer(GsonSerializer())
        val queues = JobQueueRegistry().also { it.addQueue(FANOUT_QUEUE_NAME, queue) }
        val jobs = DefaultJobDispatcher(queues, handlerRegistry, jobSerializer, tm)
        val events = DefaultEventDispatcher(tm, jobs, DefaultEventSerializer(GsonSerializer()))
        events.subscribe(ExplodingQueuedHandler())
        jobs.registerHandler<BoomJob>(object: JobHandler<BoomJob> {
            override fun execute(job: BoomJob) = error("boom del job")
        })

        val processor = JobProcessor(handlerRegistry, jobSerializer, queue, 1)
        processor.start()
        try {
            tm.transactional { events.publish(BoomEvent()) }
            await("el handler encolado se consumió") { queue.metrics().deleted >= 1 }
            val afterQueuedHandler = queue.metrics()

            jobs.dispatch(BoomJob())
            Thread.sleep(40)
            val midFlight = queue.metrics()
            Thread.sleep(120)
            val afterRetry = queue.metrics()

            // El ProcessEventHandlerJob se borra aunque on() haya tirado: invokeEventHandler traga.
            assertThat(afterQueuedHandler.deleted).isEqualTo(1)
            assertThat(afterQueuedHandler.delivered).isEqualTo(1)
            // El Job común no se borra y vuelve pasado el visibility timeout.
            assertThat(midFlight.deleted).isEqualTo(1)
            assertThat(afterRetry.delivered).isGreaterThanOrEqualTo(2)
        } finally {
            processor.stop(2)
        }
    }

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 3_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Timeout esperando: $what")
    }
}

class BoomEvent: Event()

class ExplodingQueuedHandler: QueuedEventHandler(FANOUT_QUEUE_NAME) {
    override val eventTypes = listOf(BoomEvent::class)
    override fun on(event: Event) = error("boom del handler encolado")
}

class BoomJob: Job()

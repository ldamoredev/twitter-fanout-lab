package lab.fanout.platform.queues

import lab.fanout.core.timelines.FanoutStats
import lab.fanout.core.timelines.FanoutStatsSource

/**
 * Adaptador: los contadores de la cola, contados como jobs de fan-out. Un job procesado es uno
 * borrado — mientras el worker lo tiene en la mano sigue contando como pendiente.
 */
class QueueFanoutStats(private val queue: InMemoryMessageQueue): FanoutStatsSource {
    override fun fanoutStats(): FanoutStats {
        val metrics = queue.metrics()
        return FanoutStats(
            jobsEnqueued = metrics.enqueued,
            jobsProcessed = metrics.deleted,
            jobsPending = metrics.pending + metrics.inFlight,
        )
    }
}

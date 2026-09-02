package lab.fanout.platform.queues

/**
 * Trantor no expone métricas de jobs (el `HttpServer` sí tiene `stats`), así que el número del
 * fan-out sale de la cola: cuántos jobs se generaron y cuántos se procesaron.
 */
data class QueueMetrics(
    val name: String,
    val enqueued: Long,
    val delivered: Long,
    val deleted: Long,
    val inFlight: Int,
    val pending: Int,
)

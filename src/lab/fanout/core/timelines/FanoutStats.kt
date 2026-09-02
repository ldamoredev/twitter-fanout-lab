package lab.fanout.core.timelines

/** Foto de la cola de fan-out. `jobsPending` es lo que todavía no escribió ningún timeline. */
data class FanoutStats(val jobsEnqueued: Long, val jobsProcessed: Long, val jobsPending: Int)

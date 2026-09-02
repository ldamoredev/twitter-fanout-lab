package lab.fanout.core.timelines

/** Puerto: de dónde salen los números del fan-out. Lo implementa la cola, en `platform`. */
interface FanoutStatsSource {
    fun fanoutStats(): FanoutStats
}

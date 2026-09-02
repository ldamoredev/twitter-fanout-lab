package lab.fanout.core.timelines

import dev.botta.cqbus.identity.Identity
import dev.botta.cqbus.requests.Query
import dev.botta.cqbus.requests.handlers.RequestHandler

class GetFanoutStats: Query<FanoutStats> {
    internal class Handler(private val source: FanoutStatsSource): RequestHandler<GetFanoutStats, FanoutStats> {
        override fun execute(request: GetFanoutStats, identity: Identity) = source.fanoutStats()
    }
}

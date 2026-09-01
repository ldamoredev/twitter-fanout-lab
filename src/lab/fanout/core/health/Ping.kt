package lab.fanout.core.health

import dev.botta.cqbus.identity.Identity
import dev.botta.cqbus.requests.Query
import dev.botta.cqbus.requests.handlers.RequestHandler

class Ping: Query<Ping.Status> {
    data class Status(val status: String)

    internal class Handler: RequestHandler<Ping, Status> {
        override fun execute(request: Ping, identity: Identity) = Status("ok")
    }
}

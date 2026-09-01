package lab.fanout.core.follows

import dev.botta.trantor.domain.errors.InvalidArgumentError

class CannotFollowYourself: InvalidArgumentError("followeeId", "A user cannot follow themselves")

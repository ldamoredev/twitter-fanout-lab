package lab.fanout.core.identity

import com.github.f4b6a3.uuid.UuidCreator
import dev.botta.trantor.domain.Id
import java.util.UUID

class UserId(rawId: UUID = UuidCreator.getTimeOrderedEpoch()): Id(rawId)

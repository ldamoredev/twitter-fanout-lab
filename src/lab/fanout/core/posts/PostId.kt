package lab.fanout.core.posts

import com.github.f4b6a3.uuid.UuidCreator
import dev.botta.trantor.domain.Id
import java.util.UUID

class PostId(rawId: UUID = UuidCreator.getTimeOrderedEpoch()): Id(rawId)

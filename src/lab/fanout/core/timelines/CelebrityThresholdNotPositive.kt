package lab.fanout.core.timelines

import dev.botta.trantor.domain.errors.InvalidArgumentError

class CelebrityThresholdNotPositive(followers: Int): InvalidArgumentError(
    "followers",
    "Celebrity threshold is $followers followers; a threshold below 1 would make everyone a celebrity",
)

package lab.fanout.core.posts

import dev.botta.trantor.domain.errors.InvalidArgumentError

class PostTextTooLong(lengthChars: Int): InvalidArgumentError(
    "text",
    "Post text is $lengthChars chars; max is $MAX_POST_TEXT_CHARS chars",
)

package lab.fanout.core.posts

import dev.botta.time.Clock
import dev.botta.trantor.domain.errors.ArgumentCannotBeEmptyError
import lab.fanout.core.identity.UserId
import java.time.LocalDateTime

class Post(
    val id: PostId = PostId(),
    val authorId: UserId,
    val text: String,
    val createdAt: LocalDateTime = Clock.now(),
) {
    init {
        if (text.isBlank()) throw ArgumentCannotBeEmptyError("text")
        if (text.length > MAX_POST_TEXT_CHARS) throw PostTextTooLong(text.length)
    }
}

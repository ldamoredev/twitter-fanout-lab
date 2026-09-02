package lab.fanout.core.posts

import dev.botta.trantor.primitives.events.Event
import lab.fanout.core.identity.UserId

/**
 * Se publica adentro de `EventDispatcher.defer`, no con `JobDispatcher.dispatch`. `defer` sólo
 * bufferiza eventos: un `jobs.dispatch` adentro del bloque corre en el acto.
 */
class PostPublished(val postId: PostId, val authorId: UserId): Event()

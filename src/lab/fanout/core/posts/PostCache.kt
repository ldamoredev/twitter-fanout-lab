package lab.fanout.core.posts

import dev.botta.trantor.core.cache.InMemoryCacheFactory
import dev.botta.trantor.core.cache.InMemoryCacheSettings
import kotlin.time.Duration.Companion.hours

/**
 * Default de Trantor: `expireAfter = 1.minutes`, `maximumSize = 1000`. Un minuto es corto para
 * un lab que se deja abierto, y 1000 entradas no cubren una ventana de 800 por lector con
 * varios posts. El cache no se registra solo: `CacheModule` da la factory, esta clase es el
 * servicio que el lab construye.
 */
val POST_CACHE_EXPIRE_AFTER = 1.hours
const val POST_CACHE_MAXIMUM_SIZE = 10_000L

class PostCache(factory: InMemoryCacheFactory) {
    private val cache = factory.create<PostId, PostSnapshot>(
        InMemoryCacheSettings(
            expireAfter = POST_CACHE_EXPIRE_AFTER,
            maximumSize = POST_CACHE_MAXIMUM_SIZE,
        )
    )

    fun put(post: Post) {
        cache.put(post.id, PostSnapshot.of(post))
    }

    fun get(id: PostId, load: (PostId) -> Post): Post =
        cache.get(id) { PostSnapshot.of(load(it)) }.toPost()
}

package lab.fanout.doubles

import dev.botta.trantor.core.cache.DefaultInMemoryCacheFactory
import dev.botta.trantor.core.tx.NullTransactionManager
import lab.fanout.core.posts.PostCache

fun testPostCache() = PostCache(DefaultInMemoryCacheFactory(NullTransactionManager()))

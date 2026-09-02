package lab.fanout.core.follows

import lab.fanout.core.identity.UserId

interface Follows {
    fun add(follow: Follow)
    fun followeesOf(followerId: UserId): List<UserId>
    fun followersOf(followeeId: UserId): List<UserId>

    /**
     * Separado de `followersOf` a propósito: decidir si alguien es celebridad no puede costar
     * traer 50 millones de ids. En Postgres esto es un `count`, no un `select`.
     */
    fun followersCount(followeeId: UserId): Int
}

package lab.fanout.core.timelines

import dev.botta.trantor.di.valueresolvers.config.ConfigValue

/**
 * De dónde sale el número, que es el aprendizaje del slice:
 *
 * El fan-out on write es el default porque saca el costo del camino de lectura. Deja de serlo
 * cuando el burst de un solo post no lo absorbe la cola: con `FANOUT_CHUNK_FOLLOWERS = 100`,
 * 10.000 seguidores son 101 jobs, y S2 midió ~14 ms por cada 1.000 escrituras in-memory, así que
 * un post en el umbral tarda ~140 ms en propagarse. Un millón de seguidores serían 10.001 jobs.
 *
 * El otro lado tira para arriba: cada celebridad que seguís agrega una consulta a **todas** tus
 * lecturas. Si el umbral es bajo, todos son celebridades y leer vuelve a costar O(seguidos), que
 * es exactamente el fan-out on read que S1 descartó.
 *
 * 10.000 deja el conjunto de celebridades chico (en Twitter real, mucho menos del 1% de las
 * cuentas) sin que ningún post genere un burst que la cola no drene en menos de un segundo. El
 * número correcto no sale de la teoría: sale de medir a qué ritmo drena la cola de producción.
 */
const val CELEBRITY_THRESHOLD_FOLLOWERS = 10_000

/**
 * El umbral como objeto y no como constante suelta, para que los tests lo puedan bajar a 50 y
 * para que `TRANTOR__FANOUT__CELEBRITY_THRESHOLD_FOLLOWERS` lo pueda mover sin recompilar.
 */
class CelebrityThreshold(
    @ConfigValue("fanout.celebrityThresholdFollowers")
    val followers: Int = CELEBRITY_THRESHOLD_FOLLOWERS,
) {
    init {
        if (followers < 1) throw CelebrityThresholdNotPositive(followers)
    }

    /** Celebridad es estar **por encima** del umbral: justo en el número todavía hay fan-out. */
    fun exceededBy(followerCount: Int) = followerCount > followers
}

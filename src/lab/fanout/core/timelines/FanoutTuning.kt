package lab.fanout.core.timelines

/**
 * Seguidores por job de escritura. Es la perilla del slice: con 1.000 seguidores son 10 jobs de
 * 100 escrituras cada uno, más el job que los reparte. Bajarla da más paralelismo y más overhead
 * de cola; subirla, al revés.
 */
const val FANOUT_CHUNK_FOLLOWERS = 100

/** Workers concurrentes del `JobProcessor` que consume la cola de fan-out. */
const val FANOUT_WORKERS = 8

/** Única cola del lab, así que también es la default del `JobQueueRegistry`. */
const val FANOUT_QUEUE_NAME = "fanout"

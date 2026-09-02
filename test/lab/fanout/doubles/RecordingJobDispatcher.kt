package lab.fanout.doubles

import dev.botta.trantor.core.jobs.Job
import dev.botta.trantor.core.jobs.JobDispatcher
import dev.botta.trantor.core.jobs.JobHandler
import dev.botta.trantor.core.queues.EnqueueOptions
import kotlin.reflect.KClass

/** Doble del `JobDispatcher` de Trantor: anota lo despachado en vez de encolarlo. */
class RecordingJobDispatcher: JobDispatcher {
    val dispatched = mutableListOf<Job>()

    override fun dispatch(job: Job, queueName: String?, options: EnqueueOptions) {
        dispatched.add(job)
    }

    override fun <T: Job> registerHandler(jobType: KClass<T>, handler: JobHandler<T>) = Unit

    inline fun <reified T: Job> only(): List<T> = dispatched.filterIsInstance<T>()
}

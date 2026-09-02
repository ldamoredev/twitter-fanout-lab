package lab.fanout.doubles

import dev.botta.trantor.core.events.EventListenerHandler
import dev.botta.trantor.primitives.events.Event
import dev.botta.trantor.primitives.events.EventDispatcher
import dev.botta.trantor.primitives.events.EventHandler
import dev.botta.trantor.primitives.events.EventListener
import kotlin.reflect.KClass

/**
 * Copia la semántica de `DefaultEventDispatcher.defer`: `publish` adentro del bloque se bufferiza
 * y los handlers corren después. Sirve para probar que el fan-out no se dispara a mitad de la
 * escritura, sin arrastrar JobDispatcher + serializer + tx.
 */
class RecordingEventDispatcher: EventDispatcher {
    var deferCalls = 0
    val published = mutableListOf<Event>()
    private val handlers = mutableListOf<EventHandler>()
    private val deferring = ThreadLocal.withInitial { false }
    private val buffer = ThreadLocal.withInitial { mutableListOf<Event>() }

    override fun publish(event: Event) {
        if (deferring.get()) {
            buffer.get().add(event)
            return
        }
        dispatch(event)
    }

    override fun subscribe(handler: EventHandler) {
        handlers.add(handler)
    }

    override fun defer(block: () -> Unit) {
        if (deferring.get()) {
            block()
            return
        }
        deferCalls += 1
        deferring.set(true)
        try {
            block()
        } finally {
            deferring.set(false)
            val events = buffer.get().toList()
            buffer.get().clear()
            events.forEach(::dispatch)
        }
    }

    override fun <T: Event> on(eventType: KClass<T>, listener: EventListener<T>) {
        subscribe(EventListenerHandler(eventType, listener))
    }

    private fun dispatch(event: Event) {
        published.add(event)
        handlers.filter { handler -> handler.eventTypes.any { it.isInstance(event) } }
            .forEach { it.on(event) }
    }
}

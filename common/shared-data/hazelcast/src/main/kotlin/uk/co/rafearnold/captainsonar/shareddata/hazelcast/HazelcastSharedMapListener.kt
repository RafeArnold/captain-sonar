package uk.co.rafearnold.captainsonar.shareddata.hazelcast

import com.hazelcast.core.EntryEvent
import com.hazelcast.map.listener.EntryAddedListener
import com.hazelcast.map.listener.EntryExpiredListener
import com.hazelcast.map.listener.EntryRemovedListener
import com.hazelcast.map.listener.EntryUpdatedListener
import uk.co.rafearnold.captainsonar.shareddata.EntryAddedEvent
import uk.co.rafearnold.captainsonar.shareddata.EntryExpiredEvent
import uk.co.rafearnold.captainsonar.shareddata.EntryRemovedEvent
import uk.co.rafearnold.captainsonar.shareddata.EntryUpdatedEvent
import uk.co.rafearnold.captainsonar.shareddata.SharedMapEventHandler

class HazelcastSharedMapListener<K, V>(
    private val handler: SharedMapEventHandler<K, V>
) : EntryAddedListener<K, V>, EntryUpdatedListener<K, V>, EntryRemovedListener<K, V>, EntryExpiredListener<K, V> {

    override fun entryAdded(event: EntryEvent<K, V>) {
        handler.handle(event = EntryAddedEvent(key = event.key, newValue = event.value))
    }

    override fun entryUpdated(event: EntryEvent<K, V>) {
        handler.handle(event = EntryUpdatedEvent(key = event.key, oldValue = event.oldValue, newValue = event.value))
    }

    override fun entryRemoved(event: EntryEvent<K, V>) {
        handler.handle(event = EntryRemovedEvent(key = event.key, oldValue = event.oldValue))
    }

    override fun entryExpired(event: EntryEvent<K, V>) {
        handler.handle(event = EntryExpiredEvent(key = event.key, oldValue = event.oldValue))
    }
}

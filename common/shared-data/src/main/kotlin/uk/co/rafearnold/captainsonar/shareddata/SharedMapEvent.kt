package uk.co.rafearnold.captainsonar.shareddata

sealed interface SharedMapEvent<K, V>

data class EntryAddedEvent<K, V>(val key: K, val newValue: V) : SharedMapEvent<K, V>

data class EntryUpdatedEvent<K, V>(val key: K, val oldValue: V, val newValue: V) : SharedMapEvent<K, V>

data class EntryRemovedEvent<K, V>(val key: K, val oldValue: V) : SharedMapEvent<K, V>

data class EntryExpiredEvent<K, V>(val key: K, val oldValue: V) : SharedMapEvent<K, V>

package uk.co.rafearnold.captainsonar.config

import java.util.function.Predicate

interface ObservableMap<K, V> : Map<K, V> {

    fun addListener(keyMatcher: Predicate<K>, listener: Listener<K, V>): String

    fun removeListener(listenerId: String)

    fun interface Listener<K, V> {
        fun handle(event: ListenEvent<K, V>)
    }

    interface ListenEvent<K, V> {
        val key: K
        val oldValue: V?
        val newValue: V?
    }
}

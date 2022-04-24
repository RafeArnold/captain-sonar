package uk.co.rafearnold.captainsonar.shareddata

fun interface SharedMapEventHandler<K, V> {
    fun handle(event: SharedMapEvent<K, V>)
}

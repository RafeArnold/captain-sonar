package uk.co.rafearnold.captainsonar.shareddata

fun SharedDataService.getLocalLong(name: String): SharedAtomicLong =
    this.getLong(name = name, localOnly = true)

fun SharedDataService.getDistributedLong(name: String): SharedAtomicLong =
    this.getLong(name = name, localOnly = false)

fun SharedDataService.getLocalLock(name: String): SharedLock =
    this.getLock(name = name, localOnly = true)

fun SharedDataService.getDistributedLock(name: String): SharedLock =
    this.getLock(name = name, localOnly = false)

fun <K : Any, V : Any> SharedDataService.getLocalMap(name: String): SharedMap<K, V> =
    this.getMap(name = name, localOnly = true)

fun <K : Any, V : Any> SharedDataService.getDistributedMap(name: String): SharedMap<K, V> =
    this.getMap(name = name, localOnly = false)

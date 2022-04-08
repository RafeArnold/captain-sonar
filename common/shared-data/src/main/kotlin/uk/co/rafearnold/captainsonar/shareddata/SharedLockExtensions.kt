package uk.co.rafearnold.captainsonar.shareddata

inline fun <T> SharedLock.withLock(action: () -> T): T {
    this.lock()
    try {
        return action()
    } finally {
        this.unlock()
    }
}

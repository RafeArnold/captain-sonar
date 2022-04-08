package uk.co.rafearnold.captainsonar.shareddata

/**
 * Interface for a collection (or map) that implements TTL (time-to-live) on its elements.
 */
interface TtlCollection {

    /**
     * The default time-to-live of this collection's elements, in milliseconds.
     *
     * A value of zero means the element will remain in the collection indefinitely.
     *
     * A value of less than zero is not allowed.
     *
     * For now, the value of this property is local to the instance and not distributed across the
     * cluster. This could be updated in the future to be distributed, so each instance of the same
     * shared collection has the same default TTL.
     *
     * @throws IllegalArgumentException when a value of less than zero is set.
     */
    var defaultTtlMillis: Long
}

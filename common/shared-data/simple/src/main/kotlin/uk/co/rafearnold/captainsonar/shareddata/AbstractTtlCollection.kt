package uk.co.rafearnold.captainsonar.shareddata

abstract class AbstractTtlCollection : TtlCollection {

    override var defaultTtlMillis: Long = 0
        set(value) {
            if (value < 0) throw IllegalArgumentException("Default time-to-live value must be zero or greater")
            field = value
        }
}

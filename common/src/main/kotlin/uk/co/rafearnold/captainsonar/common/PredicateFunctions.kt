package uk.co.rafearnold.captainsonar.common

import java.util.function.Predicate

@Suppress("UNCHECKED_CAST")
fun <T> alwaysTrue() = AlwaysTrue as Predicate<T>

object AlwaysTrue : Predicate<Any> {
    override fun test(t: Any): Boolean = true
}

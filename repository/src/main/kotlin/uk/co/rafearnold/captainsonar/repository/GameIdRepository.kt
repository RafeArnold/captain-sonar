package uk.co.rafearnold.captainsonar.repository

interface GameIdRepository {

    fun getAndIncrementIdIndex(): Int
}

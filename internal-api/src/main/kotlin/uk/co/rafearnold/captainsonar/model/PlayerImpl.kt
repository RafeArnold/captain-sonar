package uk.co.rafearnold.captainsonar.model

data class PlayerImpl(
    override val id: String,
    override val name: String
) : Player

package uk.co.rafearnold.captainsonar.restapiv1.model.mapper

import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.GameEvent
import uk.co.rafearnold.captainsonar.restapiv1.model.GameEventRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.GameStateRestApiV1Model

interface RestApiV1ModelMapper {

    fun mapToGameStateRestApiV1Model(game: Game, userId: String): GameStateRestApiV1Model

    fun mapToGameEventRestApiV1Model(event: GameEvent, userId: String): GameEventRestApiV1Model
}

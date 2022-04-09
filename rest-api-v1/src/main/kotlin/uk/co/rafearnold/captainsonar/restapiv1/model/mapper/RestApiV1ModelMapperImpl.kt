package uk.co.rafearnold.captainsonar.restapiv1.model.mapper

import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.GameDeletedEvent
import uk.co.rafearnold.captainsonar.model.GameEvent
import uk.co.rafearnold.captainsonar.model.GameStartedEvent
import uk.co.rafearnold.captainsonar.model.Player
import uk.co.rafearnold.captainsonar.model.PlayerAddedEvent
import uk.co.rafearnold.captainsonar.restapiv1.model.GameEndedEventRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.GameEventRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.GameStartedEventRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.GameStateRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.PlayerJoinedEventRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.PlayerRestApiV1Model

class RestApiV1ModelMapperImpl : RestApiV1ModelMapper {

    override fun mapToGameStateRestApiV1Model(game: Game, userId: String): GameStateRestApiV1Model =
        GameStateRestApiV1Model(
            id = game.id,
            hosting = game.hostId == userId,
            players = game.players.values.map { mapToPlayerRestApiV1Model(it) },
            started = game.started
        )

    private fun mapToPlayerRestApiV1Model(player: Player): PlayerRestApiV1Model =
        PlayerRestApiV1Model(name = player.name)

    override fun mapToGameEventRestApiV1Model(event: GameEvent, userId: String): GameEventRestApiV1Model =
        when (event) {
            is GameDeletedEvent -> GameEndedEventRestApiV1Model
            is GameStartedEvent -> {
                GameStartedEventRestApiV1Model(
                    gameState = mapToGameStateRestApiV1Model(game = event.game, userId = userId)
                )
            }
            is PlayerAddedEvent -> {
                PlayerJoinedEventRestApiV1Model(
                    gameState = mapToGameStateRestApiV1Model(game = event.game, userId = userId)
                )
            }
        }
}

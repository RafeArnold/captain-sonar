package uk.co.rafearnold.captainsonar.model.mapper

import uk.co.rafearnold.captainsonar.event.model.GameDeletedEventEventApiV1Model
import uk.co.rafearnold.captainsonar.event.model.GameEventApiV1Model
import uk.co.rafearnold.captainsonar.event.model.GameEventEventApiV1Model
import uk.co.rafearnold.captainsonar.event.model.GameStartedEventEventApiV1Model
import uk.co.rafearnold.captainsonar.event.model.PlayerAddedEventEventApiV1Model
import uk.co.rafearnold.captainsonar.event.model.PlayerEventApiV1Model
import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.GameDeletedEvent
import uk.co.rafearnold.captainsonar.model.GameEvent
import uk.co.rafearnold.captainsonar.model.GameStartedEvent
import uk.co.rafearnold.captainsonar.model.Player
import uk.co.rafearnold.captainsonar.model.PlayerAddedEvent
import uk.co.rafearnold.captainsonar.model.factory.GameEventFactory
import uk.co.rafearnold.captainsonar.model.factory.GameFactory
import uk.co.rafearnold.captainsonar.model.factory.PlayerFactory
import uk.co.rafearnold.captainsonar.repository.StoredGame
import uk.co.rafearnold.captainsonar.repository.StoredPlayer
import javax.inject.Inject

class ModelMapperImpl @Inject constructor(
    private val gameFactory: GameFactory,
    private val gameEventFactory: GameEventFactory,
    private val playerFactory: PlayerFactory
) : ModelMapper {

    override fun mapToGame(gameId: String, storedGame: StoredGame): Game =
        gameFactory.create(
            id = gameId,
            hostId = storedGame.hostId,
            players = storedGame.players
                .mapValues { (playerId: String, storedPlayer: StoredPlayer) ->
                    mapToPlayer(playerId = playerId, storedPlayer = storedPlayer)
                },
            started = storedGame.started
        )

    private fun mapToPlayer(playerId: String, storedPlayer: StoredPlayer) =
        playerFactory.create(id = playerId, name = storedPlayer.name)

    override fun mapToStoredGame(game: Game): StoredGame =
        StoredGame(
            hostId = game.hostId,
            players = game.players.mapValues { (_, player: Player) -> mapToStoredPlayer(player) },
            started = game.started
        )

    private fun mapToStoredPlayer(player: Player) =
        StoredPlayer(name = player.name)

    override fun mapToGameEventEventApiV1Model(gameId: String, event: GameEvent): GameEventEventApiV1Model =
        when (event) {
            is GameDeletedEvent -> GameDeletedEventEventApiV1Model(gameId = gameId)
            is GameStartedEvent -> GameStartedEventEventApiV1Model(
                gameId = gameId,
                game = mapToGameEventApiV1Model(game = event.game)
            )
            is PlayerAddedEvent -> PlayerAddedEventEventApiV1Model(
                gameId = gameId,
                game = mapToGameEventApiV1Model(game = event.game)
            )
        }

    private fun mapToGameEventApiV1Model(game: Game): GameEventApiV1Model =
        GameEventApiV1Model(
            id = game.id,
            hostId = game.hostId,
            players = game.players.mapValues { (_, player: Player) -> mapToPlayerEventApiV1Model(player = player) },
            started = game.started
        )

    private fun mapToPlayerEventApiV1Model(player: Player): PlayerEventApiV1Model =
        PlayerEventApiV1Model(
            id = player.id,
            name = player.name
        )

    override fun mapToGameEventPair(event: GameEventEventApiV1Model): Pair<String, GameEvent> =
        event.gameId to mapToGameEvent(event)

    private fun mapToGameEvent(event: GameEventEventApiV1Model): GameEvent =
        when (event) {
            is GameDeletedEventEventApiV1Model -> gameEventFactory.createGameDeletedEvent()
            is GameStartedEventEventApiV1Model -> gameEventFactory.createGameStartedEvent(game = mapToGame(game = event.game))
            is PlayerAddedEventEventApiV1Model -> gameEventFactory.createPlayerAddedEvent(game = mapToGame(game = event.game))
        }

    private fun mapToGame(game: GameEventApiV1Model): Game =
        gameFactory.create(
            id = game.id,
            hostId = game.hostId,
            players = game.players.mapValues { (_, player: PlayerEventApiV1Model) -> mapToPlayer(player = player) },
            started = game.started
        )

    private fun mapToPlayer(player: PlayerEventApiV1Model): Player =
        playerFactory.create(
            id = player.id,
            name = player.name
        )
}

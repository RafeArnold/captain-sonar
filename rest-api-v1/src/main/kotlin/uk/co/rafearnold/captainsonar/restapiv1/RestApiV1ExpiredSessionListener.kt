package uk.co.rafearnold.captainsonar.restapiv1

import uk.co.rafearnold.captainsonar.GameService
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.repository.session.SessionEvent
import uk.co.rafearnold.captainsonar.repository.session.SessionEventService
import uk.co.rafearnold.captainsonar.repository.session.SessionExpiredEvent
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class RestApiV1ExpiredSessionListener @Inject constructor(
    private val sessionEventService: SessionEventService,
    private val gameService: GameService,
    private val sessionService: RestApiV1SessionService,
) : Register {

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            sessionEventService.subscribeToSessionEvents { event: SessionEvent ->
                when (event) {
                    is SessionExpiredEvent -> {
                        val gameId: String? = sessionService.getGameId(session = event.session)
                        if (gameId != null) {
                            gameService.timeoutPlayer(
                                gameId = gameId,
                                playerId = sessionService.getUserId(session = event.session),
                            )
                        }
                    }
                }
            }
        }
}

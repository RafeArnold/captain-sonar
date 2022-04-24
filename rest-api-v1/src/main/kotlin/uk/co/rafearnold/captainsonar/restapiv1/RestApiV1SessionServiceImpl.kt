package uk.co.rafearnold.captainsonar.restapiv1

import io.vertx.ext.web.Session

class RestApiV1SessionServiceImpl : RestApiV1SessionService {

    override fun getUserId(session: Session): String = session.id()

    override fun getGameId(session: Session): String? = session.get(gameIdKey)

    override fun setGameId(session: Session, gameId: String) {
        session.put(gameIdKey, gameId)
    }

    override fun removeGameId(session: Session) {
        session.remove<Any>(gameIdKey)
    }

    companion object {
        private const val gameIdKey = "uk.co.rafearnold.captainsonar.game-id"
    }
}

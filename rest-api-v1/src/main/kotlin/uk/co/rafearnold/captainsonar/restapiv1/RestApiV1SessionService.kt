package uk.co.rafearnold.captainsonar.restapiv1

import io.vertx.ext.web.Session

interface RestApiV1SessionService {

    fun getUserId(session: Session): String

    fun getGameId(session: Session): String?

    fun setGameId(session: Session, gameId: String)

    fun removeGameId(session: Session)
}

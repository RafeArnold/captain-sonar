package uk.co.rafearnold.captainsonar.restapiv1

import io.netty.handler.codec.http.HttpResponseStatus
import uk.co.rafearnold.captainsonar.common.GameAlreadyExistsException
import uk.co.rafearnold.captainsonar.common.GameAlreadyStartedException
import uk.co.rafearnold.captainsonar.common.IllegalGameStateException
import uk.co.rafearnold.captainsonar.common.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.common.NoSuchPlayerFoundException
import uk.co.rafearnold.captainsonar.common.PlayerAlreadyJoinedGameException
import uk.co.rafearnold.captainsonar.common.UserIsNotHostException

class RestApiV1ExceptionMapper {

    fun mapToRestApiV1Exception(exception: IllegalGameStateException): RestApiV1Exception =
        when (exception) {
            is GameAlreadyExistsException -> {
                RestApiV1Exception(
                    statusCode = HttpResponseStatus.CONFLICT.code(),
                    message = "A game with the requested ID already exists"
                )
            }
            is GameAlreadyStartedException -> {
                RestApiV1Exception(
                    statusCode = HttpResponseStatus.CONFLICT.code(),
                    message = "Game has already started"
                )
            }
            is NoSuchGameFoundException -> {
                RestApiV1Exception(
                    statusCode = HttpResponseStatus.NOT_FOUND.code(),
                    message = "No game found"
                )
            }
            is PlayerAlreadyJoinedGameException -> {
                RestApiV1Exception(
                    statusCode = HttpResponseStatus.CONFLICT.code(),
                    message = "Player has already joined this game"
                )
            }
            is NoSuchPlayerFoundException -> {
                RestApiV1Exception(
                    statusCode = HttpResponseStatus.NOT_FOUND.code(),
                    message = "No player with the requested ID found"
                )
            }
            is UserIsNotHostException -> {
                RestApiV1Exception(
                    statusCode = HttpResponseStatus.FORBIDDEN.code(),
                    message = "Only the host can perform this operation"
                )
            }
        }
}

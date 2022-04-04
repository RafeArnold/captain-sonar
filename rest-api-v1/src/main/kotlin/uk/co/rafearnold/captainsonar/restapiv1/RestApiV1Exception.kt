package uk.co.rafearnold.captainsonar.restapiv1

class RestApiV1Exception(
    val statusCode: Int,
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

package uk.co.rafearnold.captainsonar.eventapiv1.model

import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "event-type"
)
sealed interface GameEventEventApiV1Model {
    val gameId: String
}

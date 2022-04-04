package uk.co.rafearnold.captainsonar.guice

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import javax.inject.Provider

class ObjectMapperProvider : Provider<ObjectMapper> {

    override fun get(): ObjectMapper = jacksonObjectMapper()
}

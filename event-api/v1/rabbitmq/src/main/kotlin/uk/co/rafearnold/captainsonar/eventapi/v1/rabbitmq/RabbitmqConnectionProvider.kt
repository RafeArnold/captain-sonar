package uk.co.rafearnold.captainsonar.eventapi.v1.rabbitmq

import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import uk.co.rafearnold.commons.config.ObservableMap
import uk.co.rafearnold.commons.config.addListener
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RabbitmqConnectionProvider @Inject constructor(
    private val appConfig: ObservableMap<String, String>,
) : Provider<Connection> {

    private var connection: Connection = buildConnection()

    init {
        appConfig.addListener(keyRegex = "\\Qrabbitmq.connection.\\E(?:host|port|username|password)") {
            connection = buildConnection()
        }
    }

    override fun get(): Connection = connection

    @Synchronized
    private fun buildConnection(): Connection {
        val connectionFactory = ConnectionFactory()
        connectionFactory.host = appConfig.getValue("rabbitmq.connection.host")
        connectionFactory.port = appConfig.getValue("rabbitmq.connection.port").toInt()
        connectionFactory.username = appConfig.getValue("rabbitmq.connection.username")
        connectionFactory.password = appConfig.getValue("rabbitmq.connection.password")
        return connectionFactory.newConnection()
    }
}

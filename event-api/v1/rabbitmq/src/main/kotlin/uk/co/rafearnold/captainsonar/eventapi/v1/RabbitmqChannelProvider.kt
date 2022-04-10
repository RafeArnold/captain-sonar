package uk.co.rafearnold.captainsonar.eventapi.v1

import com.rabbitmq.client.Channel
import javax.inject.Inject
import javax.inject.Provider

class RabbitmqChannelProvider @Inject constructor(
    private val connectionProvider: RabbitmqConnectionProvider
) : Provider<Channel> {

    override fun get(): Channel = connectionProvider.get().createChannel()
}

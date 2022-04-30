package uk.co.rafearnold.captainsonar.repository.shareddata

import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SharedMap
import uk.co.rafearnold.captainsonar.shareddata.getDistributedMap
import javax.inject.Inject
import javax.inject.Provider

class SharedDataSessionStoreDataProvider @Inject constructor(
    private val sharedDataService: SharedDataService,
) : Provider<SharedMap<String, SharedDataSessionImpl>> {
    override fun get(): SharedMap<String, SharedDataSessionImpl> =
        sharedDataService.getDistributedMap(name = "uk.co.rafearnold.captainsonar.repository.session.shared-data.data-map")
}

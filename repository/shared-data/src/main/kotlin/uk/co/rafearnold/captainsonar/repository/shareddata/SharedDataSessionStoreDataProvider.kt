package uk.co.rafearnold.captainsonar.repository.shareddata

import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl
import uk.co.rafearnold.commons.shareddata.SharedDataService
import uk.co.rafearnold.commons.shareddata.SharedMap
import uk.co.rafearnold.commons.shareddata.getDistributedMap
import javax.inject.Inject
import javax.inject.Provider

class SharedDataSessionStoreDataProvider @Inject constructor(
    private val sharedDataService: SharedDataService,
) : Provider<SharedMap<String, SharedDataSessionImpl>> {
    override fun get(): SharedMap<String, SharedDataSessionImpl> =
        sharedDataService.getDistributedMap(name = "uk.co.rafearnold.captainsonar.repository.session.shared-data.data-map")
}

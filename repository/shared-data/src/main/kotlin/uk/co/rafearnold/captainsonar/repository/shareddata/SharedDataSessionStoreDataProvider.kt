package uk.co.rafearnold.captainsonar.repository.shareddata

import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SharedMap
import uk.co.rafearnold.captainsonar.shareddata.getDistributedMap
import javax.inject.Inject
import javax.inject.Provider

class SharedDataSessionStoreDataProvider @Inject constructor(
    private val sharedDataService: SharedDataService,
) : Provider<SharedMap<String, ByteArray>> {
    override fun get(): SharedMap<String, ByteArray> =
        sharedDataService.getDistributedMap(name = "uk.co.rafearnold.captainsonar.repository.session.shared-data.data-map")
}

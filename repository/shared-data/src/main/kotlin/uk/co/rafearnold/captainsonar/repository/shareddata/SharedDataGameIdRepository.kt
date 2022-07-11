package uk.co.rafearnold.captainsonar.repository.shareddata

import uk.co.rafearnold.captainsonar.repository.GameIdRepository
import uk.co.rafearnold.commons.shareddata.SharedAtomicLong
import uk.co.rafearnold.commons.shareddata.SharedDataService
import uk.co.rafearnold.commons.shareddata.getDistributedLong
import javax.inject.Inject

class SharedDataGameIdRepository @Inject constructor(
    sharedDataService: SharedDataService,
) : GameIdRepository {

    private val index: SharedAtomicLong =
        sharedDataService.getDistributedLong("uk.co.rafearnold.captainsonar.repository.game-id.shared-data.index")

    override fun getAndIncrementIdIndex(): Int =
        Math.floorMod(index.getAndIncrement(), Int.MAX_VALUE.toLong()).toInt()
}

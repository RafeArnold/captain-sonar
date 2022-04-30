package uk.co.rafearnold.captainsonar.repository.shareddata

import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.co.rafearnold.captainsonar.shareddata.SharedAtomicLong
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.simple.SimpleClusterManager
import uk.co.rafearnold.captainsonar.shareddata.getDistributedLong
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class SharedDataGameIdRepositoryTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
        SimpleClusterManager.clearAllClusters()
    }

    @Test
    fun `when the index has not been instantiated then it is instantiated as 0 when first retrieved`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdRepository = SharedDataGameIdRepository(sharedDataService = sharedDataService)

        val long: SharedAtomicLong =
            sharedDataService.getDistributedLong("uk.co.rafearnold.captainsonar.repository.game-id.shared-data.index")

        assertEquals(0, long.get())

        assertEquals(0, gameIdRepository.getAndIncrementIdIndex())

        assertEquals(1, long.get())
    }

    @Test
    fun `index can be retrieved and incremented concurrently`() {
        val totalIncrements = 1000
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdRepository = SharedDataGameIdRepository(sharedDataService = sharedDataService)

        val initialIndex = 53434
        val long: SharedAtomicLong =
            sharedDataService.getDistributedLong("uk.co.rafearnold.captainsonar.repository.game-id.shared-data.index")
        long.compareAndSet(0, initialIndex.toLong())

        assertEquals(initialIndex, gameIdRepository.getAndIncrementIdIndex())

        val futures: Array<CompletableFuture<Int>> =
            Array(totalIncrements) { CompletableFuture.supplyAsync { gameIdRepository.getAndIncrementIdIndex() } }

        CompletableFuture.allOf(*futures).get(10, TimeUnit.SECONDS)

        val indices: Set<Int> = futures.map { it.get(1, TimeUnit.SECONDS) }.toSet()
        assertEquals(totalIncrements, indices.size)
        assertEquals(initialIndex + 1, indices.minOrNull())
        assertEquals(initialIndex + totalIncrements, indices.maxOrNull())

        assertEquals((initialIndex + totalIncrements + 1).toLong(), long.get())
    }

    @Test
    fun `when the index in the cluster reaches the integer maximum then the returned index is reset to 0`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdRepository = SharedDataGameIdRepository(sharedDataService = sharedDataService)

        val long: SharedAtomicLong =
            sharedDataService.getDistributedLong("uk.co.rafearnold.captainsonar.repository.game-id.shared-data.index")
        long.compareAndSet(0, Int.MAX_VALUE.toLong() - 2)

        assertEquals(Int.MAX_VALUE - 2, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(Int.MAX_VALUE - 1, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(0, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(1, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(2, gameIdRepository.getAndIncrementIdIndex())

        assertEquals(Int.MAX_VALUE.toLong() + 3, long.get())
    }

    @Test
    fun `when the index in the cluster reaches double the integer maximum then the returned index is reset to 0`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdRepository = SharedDataGameIdRepository(sharedDataService = sharedDataService)

        val long: SharedAtomicLong =
            sharedDataService.getDistributedLong("uk.co.rafearnold.captainsonar.repository.game-id.shared-data.index")
        long.compareAndSet(0, 2 * Int.MAX_VALUE.toLong() - 2)

        assertEquals(Int.MAX_VALUE - 2, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(Int.MAX_VALUE - 1, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(0, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(1, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(2, gameIdRepository.getAndIncrementIdIndex())

        assertEquals(2 * Int.MAX_VALUE.toLong() + 3, long.get())
    }

    @Test
    fun `when the index in the cluster is negative then the returned index cycles up to the integer maximum`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdRepository = SharedDataGameIdRepository(sharedDataService = sharedDataService)

        val long: SharedAtomicLong =
            sharedDataService.getDistributedLong("uk.co.rafearnold.captainsonar.repository.game-id.shared-data.index")
        long.compareAndSet(0, -2)

        assertEquals(Int.MAX_VALUE - 2, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(Int.MAX_VALUE - 1, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(0, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(1, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(2, gameIdRepository.getAndIncrementIdIndex())

        assertEquals(3, long.get())
    }
}

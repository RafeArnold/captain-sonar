package uk.co.rafearnold.captainsonar.shareddata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class AbstractTtlCollectionTest {

    @Test
    fun `the default ttl value is set to zero on construction`() {
        val collection: TtlCollection = object : AbstractTtlCollection() {}
        assertEquals(0, collection.defaultTtlMillis)
    }

    @Test
    fun `the default ttl value can not be set to less than zero`() {
        val collection: TtlCollection = object : AbstractTtlCollection() {}
        assertDoesNotThrow { collection.defaultTtlMillis = 1 }
        assertEquals(1, collection.defaultTtlMillis)
        assertThrows<IllegalArgumentException> { collection.defaultTtlMillis = -1 }
        assertEquals(1, collection.defaultTtlMillis)
        assertDoesNotThrow { collection.defaultTtlMillis = 0 }
        assertEquals(0, collection.defaultTtlMillis)
    }
}

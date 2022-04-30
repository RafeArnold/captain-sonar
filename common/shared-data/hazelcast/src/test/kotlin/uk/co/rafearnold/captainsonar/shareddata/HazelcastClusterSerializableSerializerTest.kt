package uk.co.rafearnold.captainsonar.shareddata

import com.hazelcast.config.Config
import com.hazelcast.config.SerializationConfig
import com.hazelcast.config.SerializerConfig
import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import com.hazelcast.map.IMap
import io.vertx.core.Vertx
import io.vertx.core.shareddata.impl.ClusterSerializable
import io.vertx.ext.auth.VertxContextPRNG
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HazelcastClusterSerializableSerializerTest {

    @Test
    fun `serializable objects can be serialized into a hazelcast map`() {
        val vertx: Vertx = Vertx.vertx()

        val serializerConfig: SerializerConfig =
            SerializerConfig()
                .setClass(HazelcastClusterSerializableSerializer::class.java)
                .setTypeClass(ClusterSerializable::class.java)
        val hazelcastConfig: Config =
            Config().setSerializationConfig(SerializationConfig().addSerializerConfig(serializerConfig))
        val hazelcastInstance: HazelcastInstance = Hazelcast.newHazelcastInstance(hazelcastConfig)

        val mapName1 = "test_mapName1"
        val hazelcastMap1: IMap<String, SharedDataSessionImpl> = hazelcastInstance.getMap(mapName1)

        val mapName1Key1 = "test_mapName1Key1"
        val mapName1Value1 = SharedDataSessionImpl(VertxContextPRNG.current(vertx), 10000, 16)
        mapName1Value1.data()["test_data1Key"] = "test_data1Value"
        mapName1Value1.data()["test_data2Key"] = true
        mapName1Value1.data()["test_data3Key"] = 56456
        hazelcastMap1.put(mapName1Key1, mapName1Value1)

        val result1: SharedDataSessionImpl? = hazelcastMap1[mapName1Key1]
        assertEquals(mapName1Value1.id(), result1?.id())
        assertEquals(mapName1Value1.timeout(), result1?.timeout())
        assertEquals(mapName1Value1.lastAccessed(), result1?.lastAccessed())
        assertEquals(mapName1Value1.version(), result1?.version())
        assertEquals(mapName1Value1.data(), result1?.data())

        vertx.close()
    }
}

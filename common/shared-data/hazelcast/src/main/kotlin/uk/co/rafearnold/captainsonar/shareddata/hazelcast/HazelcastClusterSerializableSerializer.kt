package uk.co.rafearnold.captainsonar.shareddata.hazelcast

import com.hazelcast.nio.ObjectDataInput
import com.hazelcast.nio.ObjectDataOutput
import com.hazelcast.nio.serialization.StreamSerializer
import io.vertx.core.buffer.Buffer
import io.vertx.core.shareddata.impl.ClusterSerializable
import java.io.IOException

class HazelcastClusterSerializableSerializer : StreamSerializer<ClusterSerializable> {

    override fun getTypeId(): Int = 1

    override fun write(output: ObjectDataOutput, serializable: ClusterSerializable) {
        output.writeString(serializable.javaClass.name)
        val buffer: Buffer = Buffer.buffer()
        serializable.writeToBuffer(buffer)
        val bytes: ByteArray = buffer.bytes
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    override fun read(input: ObjectDataInput): ClusterSerializable {
        val className: String = input.readString() ?: throw IOException("Null class name")
        val length: Int = input.readInt()
        val bytes = ByteArray(length)
        input.readFully(bytes)
        val serializable: ClusterSerializable =
            try {
                val clazz: Class<*> = loadClass(className)
                val serializable: ClusterSerializable =
                    clazz.getDeclaredConstructor().newInstance() as ClusterSerializable
                serializable.readFromBuffer(0, Buffer.buffer(bytes))
                serializable
            } catch (e: Exception) {
                throw IOException("Failed to load class $className", e)
            }
        return serializable
    }

    private fun loadClass(className: String): Class<*> {
        val tccl = Thread.currentThread().contextClassLoader
        try {
            if (tccl != null) return tccl.loadClass(className)
        } catch (ignored: ClassNotFoundException) {
            // Ignore.
        }
        return HazelcastClusterSerializableSerializer::class.java.classLoader.loadClass(className)
    }
}

@file:Suppress("RemoveRedundantBackticks")
package dev.forkhandles.embedded.kafka
import com.google.common.util.concurrent.AbstractService
import dev.forkhandles.embedded.support.RandomPortSupplier
import dev.forkhandles.embedded.support.TempDirSupplier
import kafka.server.KafkaConfig
import kafka.server.KafkaServer
import org.apache.kafka.common.utils.Time
import scala.Option
import java.io.File
import java.util.function.Supplier
import kotlin.concurrent.thread

class EmbeddedKafka(
        private val zkConnectSupplier: Supplier<String>,
        portSupplier: Supplier<Int> = RandomPortSupplier,
        private val dataDirSupplier: Supplier<File> = TempDirSupplier("kafka"),
        private val additionalBrokerProperties: Map<String, *> = emptyMap<String, Any>()
) : AbstractService(), AutoCloseable {
    val port = portSupplier.get()
    private var kafkaServer: KafkaServer? = null

    val bootstrapServers = "localhost:$port"
    @SuppressWarnings("UNCHECKED_CAST")
    override fun doStart() {
        val brokerProperties = additionalBrokerProperties + mapOf(
                KafkaConfig.ZkConnectProp() to zkConnectSupplier.get(),
                KafkaConfig.ListenersProp() to "PLAINTEXT://localhost:$port",
                KafkaConfig.LogDirProp() to dataDirSupplier.get().canonicalPath,
                KafkaConfig.OffsetsTopicReplicationFactorProp() to 1.toShort())
        KafkaServer(
                KafkaConfig(brokerProperties),
                Time.SYSTEM,
                Option.empty(),
                true)
                .also { this.kafkaServer = it }
                .also { kafkaServer ->
                    thread {
                        try {
                            kafkaServer.startup()
                            notifyStarted()
                        } catch (ex: Exception) {
                            notifyFailed(ex)
                        }
                    }
                }
    }

    override fun doStop() {
        kafkaServer?.run {
            shutdown()
            thread {
                awaitShutdown()
                notifyStopped()
            }
        }
    }

    override fun close() {
        (dataDirSupplier as? AutoCloseable)?.close()
    }
}

package dev.forkhandles.embeddedkafka.zookeeper

import dev.forkhandles.embedded.EmbeddedZookeeper
import org.junit.jupiter.api.Test


class EmbeddedZookeeperTest {

    @Test
    fun startAndStop() {
        EmbeddedZookeeper().use { embeddedZookeeper ->
            embeddedZookeeper.startAsync()
                    .awaitRunning()

            embeddedZookeeper.stopAsync()
                    .awaitTerminated()
        }
    }
}

package dev.forkhandles.embeddedkafka.kafka

import dev.forkhandles.embedded.EmbeddedZookeeper
import dev.forkhandles.embedded.kafka.EmbeddedKafka
import org.junit.jupiter.api.Test


class EmbeddedKafkaTest {

    @Test
    fun testStartStop() {
        EmbeddedZookeeper().use { zookeeper ->

            zookeeper.startAsync()
                    .awaitRunning()

            try {

                EmbeddedKafka({ zookeeper.zkConnect }).use { kafka ->
                    kafka.startAsync()
                            .awaitRunning()
                    try {

                    } finally {
                        kafka.stopAsync()
                                .awaitTerminated()
                    }
                }

            } finally {
                zookeeper.stopAsync()
                        .awaitTerminated()
            }
        }
    }
}
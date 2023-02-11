package dev.forkhandles.embeddedkafka.kafka.junit5

import dev.forkhandles.embedded.kafka.junit5.EmbeddedKafka
import org.junit.jupiter.api.Test

@EmbeddedKafka
class EmbeddedKafkaAnnotationTest {

    @Test
    @EmbeddedKafka(
            brokerProperties = ["auto.create.topics.enable=false"])
    fun test() {
    }
}
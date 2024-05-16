package dev.forkhandles.embeddedkafka.kafka

import dev.forkhandles.embedded.kafka.junit5.EmbeddedKafka
import dev.forkhandles.embedded.kafka.junit5.EmbeddedKafkaConsumer
import dev.forkhandles.embedded.kafka.junit5.EmbeddedKafkaProducer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

@EmbeddedKafka
class CanSendAndConsumeMessageTest {

    @Test
    fun `test sending of message`(
            @EmbeddedKafkaProducer
            producer: Producer<String, String>,
            @EmbeddedKafkaConsumer(
                    topics = ["test-topic"],
                    properties = ["group.id=test", "auto.offset.reset=earliest"]
            )
            consumer: KafkaConsumer<String, String>
    ) {
        producer.send(ProducerRecord("test-topic", "KEY1", "VALUE")).get()
        producer.send(ProducerRecord("test-topic", "KEY2", "VALUE")).get()

        assertEquals(2, consumer.poll(Duration.ofSeconds(10)).count())
    }
}

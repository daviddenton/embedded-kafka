# Embedded Kafka Broker

This library allows to embed a [Kafka](https://kafka.apache.org) broker inside
a Java process. It is mainly intended for integration testing Kafka client code.

It is an forked update of the amazing extension written at [unbroken-dome](https://github.com/unbroken-dome/embedded-kafka),
but simplified to just include the core module, and updated for the latest Kakfa APIs.

The library features a [JUnit 5](https://junit.org/junit5/) extension that makes it
very easy to write such tests.

Example (Gradle):

```kotlin
dependencies {
    testApi("dev.forkhandles:embedded-kafka:1.0.0")
}
```

== Using the JUnit Extension

The extension is activated by annotating your tests with `@EmbeddedKafka`. The annotation
can be placed on a class (including `@Nested` test classes) or a single test method.

It will then start an embedded Kafka broker (and an embedded Zookeeper server as well)
before each test, listening on a random free port, and shut it down after the test.


```java
@EmbeddedKafka
public class EmbeddedKafkaTest {

    @Test
    public void test() {
        // ...
    }

    @Test
    // Here we use the annotation again, overriding the broker properties
    @EmbeddedKafka(brokerProperties = "auto.create.topics.enable=false")
    public void anotherTest() {
    }
}
```

You can of course also activate the extension with
`@ExtendWith(EmbeddedKafkaExtension.class)`, if you do not need the additional configuration
options that `@EmbeddedKafka` provides.


=== Connecting to the Embedded Broker

For the embedded Kafka broker to be useful you will need to connect to it from
within your tests. The extension offers a variety of ways to do that.

The most basic way is to inject a parameter annotated with `@EmbeddedKafkaAddress`,
which receives the address of the broker. You can use this value directly for the
`bootstrap.servers` configuration property of a client.

==== Injecting the Broker Address

```java
@EmbeddedKafka
public class EmbeddedKafkaTest {

    @Test
    public void test(@EmbeddedKafkaAddress String bootstrapServers) {
        Map<String, String> config = new HashMap();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // add more configuration properties

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
            // use the producer
        }
    }
}
```


==== Exposing System Properties

It is also possible to expose the broker address as a system property, by setting the
`exposeSystemProperties` parameter of the `@EmbeddedKafka` annotation to `true`. This can be
useful if the code under test uses a framework which is configured from the
environment rather than programmatically.

```java
@EmbeddedKafka(exposeSystemProperties = true)
public class EmbeddedKafkaTest {
}
```


==== Injecting a Producer or Consumer

You can inject a ready-to-use producer or consumer directly into the test method. It will also
automatically be closed after the test.

```java
@EmbeddedKafka
public class EmbeddedKafkaTest {

    @Test
    public void testWithProducer(Producer<String, String> producer) {
        // The producer will be configured to connect to the embedded broker,
        // and use a StringSerializer for keys and values.
    }

    @Test
    public void testWithConsumer(Consumer<String, String> consumer) {
        // The consumer will be configured to connect to the embedded broker,
        // and use a StringDeserializer for keys and values.
    }
}
```

In the example above, the key and value (de)serializer types are automatically guessed based on the
type arguments of the `Producer<K, V>` parameter, if they are among the types supported by the built-in
serializers or deserializers (`StringSerializer`, `ByteArraySerializer` etc.).

You can also specify the serializer or deserializer types explicitly by placing a `@EmbeddedKafkaProducer` or
`@EmbeddedKafkaConsumer` annotation on the parameter:

```java
@EmbeddedKafka
public class EmbeddedKafkaTest {

    @Test
    public void testWithProducer(
            @EmbeddedKafkaProducer(
                keySerializerClass = StringSerializer.class,
                valueSerializerClass = StringSerializer.class)
            Producer<String, String> producer) {
        // ...
    }

    @Test
    public void testWithConsumer(
            @EmbeddedKafkaConsumer(
                keyDeserializerClass = StringDeserializer.class,
                valueDeserializerClass = StringDeserializer.class)
            Consumer<String, String> consumer) {
        // ...
    }
}
```

The `@EmbeddedKafkaConsumer` annotation also has a `topics` parameter that will automatically subscribe the
consumer to the given topics, and unsubscribe after the test:

```java
@EmbeddedKafka
public class EmbeddedKafkaTest {

    @Test
    public void testWithConsumer(
            @EmbeddedKafkaConsumer(topics = "test-topic")
            Consumer<String, String> consumer) {
        // Here the consumer will already be subscribed to test-topic
    }
}
```

If you prefer to construct your `KafkaProducer` or `KafkaConsumer` manually, you can also use an annotated
`Map` parameter:

```
@EmbeddedKafka
public class EmbeddedKafkaTest {

    @Test
    public void testWithProducer(
            @EmbeddedKafkaProducer(
                keySerializerClass = StringSerializer.class,
                valueSerializerClass = StringSerializer.class)
            Map<String, ?> producerProperties) {
        KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties);
    }

    @Test
    public void testWithConsumer(
            @EmbeddedKafkaConsumer(
                keyDeserializerClass = StringDeserializer.class,
                valueDeserializerClass = StringDeserializer.class)
            Map<String, ?> consumerProperties) {
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties);
    }
}
```

Note that when injecting a `Map`, the serializer or deserializer types cannot be guessed - they must either be
specified in the annotation, or added to the Map programmatically.

=== Configuring Broker Properties

The configuration properties of the broker itself can be fine-tuned using two ways.

First, you can use the `brokerProperties` parameter of the `@EmbeddedKafka` annotation:

```java
@EmbeddedKafka(brokerProperties = "auto.create.topics.enable=false")
public class EmbeddedKafkaTest {

}
```

Another way is to create the properties in code, and return them from a method annotated with
`@EmbeddedKafkaProperties`:

```java
@EmbeddedKafka(brokerProperties = "auto.create.topics.enable=false")
public class EmbeddedKafkaTest {

    @EmbeddedKafkaProperties
    public Map<String, ?> brokerProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("auto.create.topics.enable", false);
        return properties;
    }
}
```

The different approaches can also be combined. In any test context, the broker properties will be merged "top down"
to the level of the individual test method:

```java
@EmbeddedKafka(brokerProperties = "auto.create.topics.enable=false")
public class EmbeddedKafkaTest {

    @Nested
    @EmbeddedKafka(brokerProperties = "auto.create.topics.enable=true")
    public class WithAutoCreatedTopics {

        @EmbeddedKafkaProperties
        public Map<String, ?> brokerProperties() {
            Map<String, Object> properties = new HashMap<>();
            properties.put("num.partitions", 3);
            return properties;
        }

        @Test
        @EmbeddedKafka(brokerProperties = "log.retention.minutes=1")
        public void test() {
            // In this test the broker will be configured with
            //   auto.create.topics.enable=true
            //   num.partitions=3
            //   log.retention.minutes=1
        }
    }

    @Test
    public void test() {
        // In this test the broker will be configured with
        //   auto.create.topics.enable=false
    }
}
```

=== Creating Topics

Topics to be used by the tests can be created by the extension. Again, there are multiple ways to accomplish this.

- Use the `topics` parameter of the `@EmbeddedKafka` annotation
- Define a method annotated with `@EmbeddedKafkaTopics` that returns a `String`, a `NewTopic`, or a collection
  containing `String`s and/or `NewTopic`s

```java
@EmbeddedKafka(createTopics = "test-topic") // define "test-topic" with the default settings
public class EmbeddedKafkaTest {

    @EmbeddedKafkaTopics
    public NewTopic topicWithSettings() {
        return new NewTopic("test-topic-2", 3, 2)
    }

    // If the method only returns topic names, the parameters from the annotation will be used
    @EmbeddedKafkaTopics(numPartitions = 3, replicationFactor = 1)
    public List<String> topicsByNameOnly() {
        return Arrays.asList("test-topic-3", "test-topic-4");
    }

    // We can also create a topic only for a single test
    @Test
    @EmbeddedKafka(createTopics = "test-topic-5")
    public void test() {
        // ...
    }

    // We can re-define a topic with the same name to override its configuration
    @Nested
    @EmbeddedKafka(createTopics = "test-topic", numPartitions = 3)
    public class WithThreeTopicPartitions {

        // ...
    }
}
```

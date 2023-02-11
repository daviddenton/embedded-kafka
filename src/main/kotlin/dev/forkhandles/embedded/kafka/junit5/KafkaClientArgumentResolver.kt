package dev.forkhandles.embedded.kafka.junit5

import dev.forkhandles.embedded.support.resolveAsClass
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import java.lang.reflect.ParameterizedType

class KafkaClientArgumentResolver : ArgumentResolver {
    private val supportedAnnotatedProducerTypes: Set<Class<*>> =
            setOf(Producer::class.java, KafkaProducer::class.java, Map::class.java)
    private val supportedUnannotatedProducerTypes: Set<Class<*>> =
            setOf(Producer::class.java, KafkaProducer::class.java)
    private val supportedAnnotatedConsumerTypes: Set<Class<*>> =
            setOf(Consumer::class.java, KafkaConsumer::class.java, Map::class.java)
    private val supportedUnannotatedConsumerTypes: Set<Class<*>> =
            setOf(Consumer::class.java, KafkaConsumer::class.java)

    private fun shouldGuessSerializerTypes(parameterType: Class<*>): Boolean =
            parameterType.isAssignableFrom(KafkaProducer::class.java)

    private fun shouldGuessDeserializerTypes(parameterType: Class<*>): Boolean =
            parameterType.isAssignableFrom(KafkaConsumer::class.java)

    private fun createProducer(expectedType: Class<*>, producerProps: Map<String, Any>): Any? =
            when {
                expectedType.isAssignableFrom(KafkaProducer::class.java) ->
                    KafkaProducer<Any, Any>(producerProps)

                expectedType == Map::class.java ->
                    producerProps

                else -> null
            }

    private fun createConsumer(expectedType: Class<*>, consumerProps: Map<String, Any>,
                               topics: List<String>): Any? =
            when {
                expectedType.isAssignableFrom(KafkaConsumer::class.java) ->
                    KafkaConsumer<Any, Any>(consumerProps)
                            .let { consumer ->
                                when {
                                    topics.isNotEmpty() -> {
                                        consumer.subscribe(topics)
                                        DisposableParameter(consumer) {
                                            unsubscribe()
                                            close()
                                        }
                                    }
                                    else -> consumer
                                }
                            }

                expectedType == Map::class.java ->
                    consumerProps

                else -> null
            }

    override fun supports(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
            parameterContext.parameter.let { parameter ->
                val hasProducerAnnotation = parameter.isAnnotationPresent(EmbeddedKafkaProducer::class.java)
                val hasConsumerAnnotation = parameter.isAnnotationPresent(EmbeddedKafkaConsumer::class.java)
                require(!(hasProducerAnnotation && hasConsumerAnnotation)) {
                    "A parameter cannot be annotated with both @EmbeddedKafkaProducer " +
                            "and @EmbeddedKafkaConsumer"
                }
                when {
                    hasProducerAnnotation -> parameter.type in supportedAnnotatedProducerTypes
                    hasConsumerAnnotation -> parameter.type in supportedAnnotatedConsumerTypes
                    else -> parameter.type in supportedUnannotatedProducerTypes ||
                            parameter.type in supportedUnannotatedConsumerTypes
                }
            }

    override fun resolve(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any? =
            parameterContext.parameter.let { parameter ->
                if (parameter.isAnnotationPresent(EmbeddedKafkaProducer::class.java) ||
                        parameter.type in supportedUnannotatedProducerTypes) {
                    resolveProducer(parameterContext, extensionContext)
                } else if (parameter.isAnnotationPresent(EmbeddedKafkaConsumer::class.java) ||
                        parameter.type in supportedUnannotatedConsumerTypes) {
                    resolveConsumer(parameterContext, extensionContext)
                } else {
                    throw IllegalArgumentException("Cannot resolve argument for ParameterContext: $parameterContext")
                }
            }

    private fun resolveProducer(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any? {
        parameterContext.parameter.let { parameter ->
            val annotation: EmbeddedKafkaProducer? =
                    parameter.getAnnotation(EmbeddedKafkaProducer::class.java)
            val serializerProps = when {
                shouldGuessSerializerTypes(parameter.type) -> {
                    val (keyType, valueType) = getKeyAndValueType(parameterContext)
                    mapOf(
                            KEY_SERIALIZER_CLASS_CONFIG to
                                    determineSerializerClass(annotation?.keySerializerClass?.java, keyType),
                            VALUE_SERIALIZER_CLASS_CONFIG to
                                    determineSerializerClass(annotation?.valueSerializerClass?.java, valueType))
                }

                else -> annotation.serializerProperties
            }
            return createProducer(parameterContext.parameter.type,
                    serializerProps + annotation.parseProducerProperties() + extensionContext.clientProperties)
        }
    }

    private fun resolveConsumer(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any? {
        parameterContext.parameter.let { parameter ->
            val annotation: EmbeddedKafkaConsumer? =
                    parameter.getAnnotation(EmbeddedKafkaConsumer::class.java)
            val deserializerProps = if (shouldGuessDeserializerTypes(parameter.type)) {
                val (keyType, valueType) = getKeyAndValueType(parameterContext)
                mapOf(
                        KEY_DESERIALIZER_CLASS_CONFIG to
                                determineDeserializerClass(annotation?.keyDeserializerClass?.java, keyType),
                        VALUE_DESERIALIZER_CLASS_CONFIG to
                                determineDeserializerClass(annotation?.valueDeserializerClass?.java, valueType))
            } else {
                annotation.deserializerProperties
            }
            return createConsumer(parameter.type,
                    deserializerProps + annotation.parseConsumerProperties() + extensionContext.clientProperties,
                    annotation?.topics?.toList() ?: emptyList())
        }
    }

    private fun getKeyAndValueType(parameterContext: ParameterContext): Pair<Class<*>?, Class<*>?> =
            (parameterContext.parameter.parameterizedType as? ParameterizedType)
                    ?.actualTypeArguments
                    ?.takeIf { it.size == 2 }
                    ?.map { it.resolveAsClass() }
                    ?.let {
                        it[0] to it[1]
                    } ?: (null to null)

    private fun determineSerializerClass(serializerClassFromAnnotation: Class<out Serializer<*>>?,
                                         itemClass: Class<*>?): Class<out Serializer<*>> =
            serializerClassFromAnnotation
                    ?.takeIf { it != Serializer::class.java }
                    ?: requireNotNull(itemClass?.let { SerializerTypeGuesser.guessSerializerType(it) }) {
                        val typeDescription = itemClass?.let { "type ${it.name}" } ?: "unknown type"
                        "Could not determine the serializer class for $typeDescription, " +
                                "please specify it explicitly in the @EmbeddedKafkaProducer annotation."
                    }

    private fun determineDeserializerClass(deserializerClassFromAnnotation: Class<out Deserializer<*>>?,
                                           itemClass: Class<*>?): Class<out Deserializer<*>> =
            deserializerClassFromAnnotation
                    ?.takeIf { it != Deserializer::class.java }
                    ?: requireNotNull(itemClass?.let { SerializerTypeGuesser.guessDeserializerType(it) }) {
                        val typeDescription = itemClass?.let { "type ${it.name}" } ?: "unknown type"
                        "Could not determine the deserializer class for $typeDescription, " +
                                "please specify it explicitly in the @EmbeddedKafkaConsumer annotation."
                    }
}

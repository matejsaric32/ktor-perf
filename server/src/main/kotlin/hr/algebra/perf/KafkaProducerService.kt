package hr.algebra.perf

import hr.algebra.perf.config.KafkaConfig
import kotlinx.serialization.json.Json
import hr.algebra.perf.model.OrderViewedEvent
import io.ktor.server.application.Application
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.Properties

class KafkaProducerService(
    private val config : KafkaConfig
) {
    private val props = Properties().apply {
        put("bootstrap.servers", config.brokers)
        put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        put("acks", config.acks)
    }
    
    private val producer = KafkaProducer<String, String>(props)
    private val json = Json { ignoreUnknownKeys = true }
    
    fun publishOrderViewed(event : OrderViewedEvent) {
        val record = ProducerRecord(
            config.topic,
            event.userId.toString(),
            json.encodeToString(event)
        )
        producer.send(record)
    }
    
    fun close() = producer.close()
}

fun Application.getKafkaConfig() : KafkaConfig {
    val config = environment.config
    return KafkaConfig(
        brokers = config.property("kafka.brokers").getString(),
        topic = config.property("kafka.topic").getString(),
        acks = config.property("kafka.acks").getString()
    )
}
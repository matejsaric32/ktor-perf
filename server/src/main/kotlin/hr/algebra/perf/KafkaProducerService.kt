package hr.algebra.perf

import kotlinx.serialization.json.Json
import hr.algebra.perf.model.OrderViewedEvent
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.Properties

class KafkaProducerService(brokers : String = "localhost:9092") {
    private val props = Properties().apply {
        put("bootstrap.servers", brokers)
        put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        put("acks", "1")
    }
    
    private val producer = KafkaProducer<String, String>(props)
    private val json = Json { ignoreUnknownKeys = true }
    
    fun publishOrderViewed(event : OrderViewedEvent) {
        val record = ProducerRecord(
            "order-events",
            event.userId.toString(),
            json.encodeToString(event)
        )
        producer.send(record)
    }
    
    fun close() = producer.close()
}
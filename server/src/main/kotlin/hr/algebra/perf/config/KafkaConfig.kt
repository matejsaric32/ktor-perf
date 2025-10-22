package hr.algebra.perf.config

data class KafkaConfig(
    val brokers : String,
    val topic : String,
    val acks : String
)

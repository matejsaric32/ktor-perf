package hr.algebra.perf.model

import kotlinx.serialization.Serializable

@Serializable
data class IOLightResponse(
    val data : OrderSummary,
    val metrics : IOMetrics
)

@Serializable
data class IOMetrics(
    val redisGetMs : Long,
    val cacheHit : Boolean,
    val dbQueryMs : Long,
    val kafkaPublishMs : Long,
    val totalMs : Long,
    val workload : String,
    val operations : Int
)

package hr.algebra.perf.model

import kotlinx.serialization.Serializable

@Serializable
data class IOResponse(
    val data : OrderSummary,
    val metrics : Map<String, Long>
)

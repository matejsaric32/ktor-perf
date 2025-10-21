package hr.algebra.perf.model

import kotlinx.serialization.Serializable

@Serializable
data class OrderViewedEvent(
    val userId : Int,
    val timestamp : Long = System.currentTimeMillis(),
    val orderCount : Int,
    val totalAmount : Double
)
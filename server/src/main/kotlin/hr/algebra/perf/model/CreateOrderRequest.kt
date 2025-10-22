package hr.algebra.perf.model

import kotlinx.serialization.Serializable

@Serializable
data class CreateOrderRequest(
    val userId: Int,
    val totalAmount: Double,
    val status: String,
    val items: List<OrderItem>
)

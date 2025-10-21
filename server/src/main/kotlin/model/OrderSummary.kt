package model

import kotlinx.serialization.Serializable

@Serializable
data class OrderSummary(
    val userId : Int,
    val orders : List<Order>
)
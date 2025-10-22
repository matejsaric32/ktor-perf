package hr.algebra.perf.model

import kotlinx.serialization.Serializable

@Serializable
data class UserStats(
    val userId : Int,
    val totalOrders : Int,
    val totalSpent : Double,
    val averageOrderValue : Double,
    val lastOrderDate : Long?
)

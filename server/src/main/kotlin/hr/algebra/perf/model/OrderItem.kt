package hr.algebra.perf.model

import kotlinx.serialization.Serializable

@Serializable
data class OrderItem(
    val productName : String,
    val quantity : Int,
    val price : Double
)

package model

import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id : Int,
    val totalAmount : Double,
    val status : String,
    val itemCount : Int
)
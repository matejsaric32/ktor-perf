package hr.algebra.perf.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class IOHeavyResponse(
    val data : IOHeavyData,
    val metrics : JsonObject
)

@Serializable
data class IOHeavyData(
    val orderSummary : OrderSummary,
    val userStats : UserStats,
    val slowQueryResults : Int,
    val fastQueryResults : Int,
    val aggregationData : JsonObject
)


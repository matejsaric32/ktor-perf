package hr.algebra.perf.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.Serializable

@Serializable
data class IOHeavyWriteResponse(
    val result : WriteResult,
    val metrics : JsonObject
)

@Serializable
data class WriteResult(
    val orderId : Int,
    val bulkInserted : Int,
    val userId : Int
)

package hr.algebra.perf.model


import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class IOStressResponse(
    val test : String,
    val metrics : JsonObject
)

package hr.algebra.perf.model

import kotlinx.serialization.Serializable

@Serializable
data class EchoRequest(
    val message : String,
    val metadata : Map<String, String> = emptyMap()
)
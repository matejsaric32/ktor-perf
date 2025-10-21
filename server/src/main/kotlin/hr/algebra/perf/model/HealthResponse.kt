package hr.algebra.perf.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status : String = "ok",
    val timestamp : Long = System.currentTimeMillis(),
    val service : String = "ktor-perf"
)

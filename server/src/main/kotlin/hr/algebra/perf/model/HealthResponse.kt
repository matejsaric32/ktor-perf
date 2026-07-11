package hr.algebra.perf.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: Long = System.currentTimeMillis(),
    val service: String = "ktor-perf",
    val checks: Map<String, CheckResult> = emptyMap()
)

@Serializable
data class CheckResult(
    val status: String,
    val message: String? = null
)

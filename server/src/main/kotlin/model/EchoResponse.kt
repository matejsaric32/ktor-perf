package model

import kotlinx.serialization.Serializable

@Serializable
data class EchoResponse(
    val original : EchoRequest,
    val timestamp : Long = System.currentTimeMillis(),
    val processedBy : String = "ktor-perf",
    val parseTimeMs : Long,
    val serializeTimeMs : Long,
    val totalTimeMs : Long
)
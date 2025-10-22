package hr.algebra.perf.model

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkEvent(
    val id : Int,
    val userId : Int,
    val sessionId : String,
    val actionType : String,
    val timestamp : Long,
    val metadata : String
)

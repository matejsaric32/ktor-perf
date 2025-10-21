package hr.algebra.perf.model

import kotlinx.serialization.Serializable

@Serializable
data class ComputeResponse(
    val result : Long,
    val iterations : Int,
    val computeTimeMs : Long
)
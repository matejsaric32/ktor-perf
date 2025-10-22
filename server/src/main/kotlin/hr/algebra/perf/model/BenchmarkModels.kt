package hr.algebra.perf.model

import kotlinx.serialization.Serializable

@Serializable
data class BenchmarkLog(
    val id : Int,
    val userId : Int,
    val sessionId : String,
    val actionType : String,
    val payload : String?,
    val ipAddress : String?,
    val randomValue : Int,
    val statusCode : Int
)

@Serializable
data class BenchmarkEvent(
    val eventType : String,
    val eventData : Map<String, String>,
    val sourceSystem : String,
    val correlationId : String
)

@Serializable
data class BenchmarkInsertRequest(
    val count : Int = 100,
    val eventType : String = "benchmark_test"
)

@Serializable
data class BenchmarkQueryResponse(
    val data : List<BenchmarkLog>,
    val totalRows : Int,
    val queryTimeMs : Long,
    val queryType : String,
    val indexed : Boolean
)

@Serializable
data class BenchmarkInsertResponse(
    val insertedCount : Int,
    val insertTimeMs : Long,
    val avgTimePerInsert : Double
)

@Serializable
data class BenchmarkAggregateResponse(
    val aggregations : Map<String, String>,
    val totalRows : Int,
    val queryTimeMs : Long,
    val queryComplexity : String
)
package hr.algebra.perf.config

data class DatabaseConfig(
    val url : String,
    val user : String,
    val password : String,
    val driver : String,
    val schema : String,
    val embedded : Boolean
)

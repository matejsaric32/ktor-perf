package hr.algebra.perf.config

data class RedisConfig(
    val host : String,
    val port : Int,
    val ttl : Int
)

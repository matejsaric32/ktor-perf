package hr.algebra.perf.config

data class DatabaseConfig(
    val jdbcUrl : String,
    val username : String,
    val password : String,
    val driverClassName : String,
    val schema : String,
    val hikari : HikariConfig
)

data class HikariConfig(
    val maximumPoolSize : Int,
    val minimumIdle : Int,
    val idleTimeout : Long,
    val connectionTimeout : Long,
    val maxLifetime : Long,
    val poolName : String,
    val autoCommit : Boolean,
    val connectionTestQuery : String
)


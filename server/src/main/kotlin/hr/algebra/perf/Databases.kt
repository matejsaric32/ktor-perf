package hr.algebra.perf

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import hr.algebra.perf.config.DatabaseConfig
import io.ktor.server.application.*
import io.ktor.util.*

val DataSourceKey = AttributeKey<HikariDataSource>("DataSource")

fun Application.configureDatabases() {
    val config = getDatabaseConfig()
    val dataSource = createHikariDataSource(config)
    attributes.put(DataSourceKey, dataSource)

    monitor.subscribe(ApplicationStopped) {
        dataSource.close()
    }
}

fun Application.getDatabaseConfig() : DatabaseConfig {
    val config = environment.config
    return DatabaseConfig(
        jdbcUrl = config.property("database.jdbcUrl").getString(),
        username = config.property("database.username").getString(),
        password = config.property("database.password").getString(),
        driverClassName = config.property("database.driverClassName").getString(),
        schema = config.property("database.schema").getString(),
        hikari = hr.algebra.perf.config.HikariConfig(
            maximumPoolSize = config.property("database.hikari.maximumPoolSize").getString().toInt(),
            minimumIdle = config.property("database.hikari.minimumIdle").getString().toInt(),
            idleTimeout = config.property("database.hikari.idleTimeout").getString().toLong(),
            connectionTimeout = config.property("database.hikari.connectionTimeout").getString().toLong(),
            maxLifetime = config.property("database.hikari.maxLifetime").getString().toLong(),
            poolName = config.property("database.hikari.poolName").getString(),
            autoCommit = config.property("database.hikari.autoCommit").getString().toBoolean(),
            connectionTestQuery = config.property("database.hikari.connectionTestQuery").getString()
        )
    )
}

fun Application.createHikariDataSource(config : DatabaseConfig) : HikariDataSource {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.username
        password = config.password
        driverClassName = config.driverClassName
        schema = config.schema

        maximumPoolSize = config.hikari.maximumPoolSize
        minimumIdle = config.hikari.minimumIdle
        idleTimeout = config.hikari.idleTimeout
        connectionTimeout = config.hikari.connectionTimeout
        maxLifetime = config.hikari.maxLifetime
        poolName = config.hikari.poolName
        isAutoCommit = config.hikari.autoCommit
        connectionTestQuery = config.hikari.connectionTestQuery
    }

    log.info("Creating HikariCP DataSource for ${config.jdbcUrl} with pool size ${config.hikari.maximumPoolSize}")
    return HikariDataSource(hikariConfig)
}

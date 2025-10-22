package hr.algebra.perf

import hr.algebra.perf.config.DatabaseConfig
import io.ktor.server.application.*
import io.ktor.server.routing.*
import java.sql.Connection
import java.sql.DriverManager

fun Application.configureDatabases() {
    routing {
    
    }
}

fun Application.getDatabaseConfig() : DatabaseConfig {
    val config = environment.config
    return DatabaseConfig(
        url = config.property("database.url").getString(),
        user = config.property("database.user").getString(),
        password = config.property("database.password").getString(),
        driver = config.property("database.driver").getString(),
        schema = config.property("database.schema").getString(),
        embedded = config.property("database.embedded").getString().toBoolean()
    )
}

fun Application.connectToDatabase(config : DatabaseConfig) : Connection {
    Class.forName(config.driver)
    
    log.info("Connecting to database at ${config.url} with schema ${config.schema}")
    return DriverManager.getConnection(config.url, config.user, config.password)
}
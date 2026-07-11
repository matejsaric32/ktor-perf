package hr.algebra.perf

import com.zaxxer.hikari.HikariDataSource
import hr.algebra.perf.model.CheckResult
import hr.algebra.perf.model.HealthResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import java.util.Properties
import java.util.concurrent.TimeUnit

fun Application.configureHealthRouting() {
    val dataSource = attributes.getOrNull(DataSourceKey)
    val kafkaConfig = getKafkaConfig()

    routing {
        get("/live") {
            call.respond(HttpStatusCode.OK, HealthResponse(status = "up"))
        }

        get("/health") {
            val dbCheck = checkDatabase(dataSource)
            val kafkaCheck = checkKafka(kafkaConfig.brokers)
            val allOk = dbCheck.status == "ok" && kafkaCheck.status == "ok"
            val overallStatus = if (allOk) "ok" else "degraded"
            val httpStatus = if (allOk) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable

            call.respond(
                httpStatus,
                HealthResponse(
                    status = overallStatus,
                    checks = mapOf("database" to dbCheck, "kafka" to kafkaCheck)
                )
            )
        }
    }
}

private fun checkDatabase(dataSource: HikariDataSource?): CheckResult {
    if (dataSource == null) return CheckResult(status = "error", message = "DataSource not initialized")
    return try {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT 1").close()
            }
        }
        CheckResult(status = "ok")
    } catch (e: Exception) {
        CheckResult(status = "error", message = e.message)
    }
}

private fun checkKafka(brokers: String): CheckResult {
    val props = Properties().apply {
        put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers)
        put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000")
        put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "3000")
    }
    return try {
        AdminClient.create(props).use { admin ->
            admin.listTopics().names().get(3, TimeUnit.SECONDS)
        }
        CheckResult(status = "ok")
    } catch (e: Exception) {
        CheckResult(status = "error", message = e.message)
    }
}

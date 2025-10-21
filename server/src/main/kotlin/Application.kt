import hr.algebra.perf.configureDatabases
import hr.algebra.perf.configureFrameworks
import hr.algebra.perf.configureHTTP
import hr.algebra.perf.configureMonitoring
import hr.algebra.perf.configureSerialization
import io.ktor.server.application.*

fun main(args : Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureHTTP()
    configureMonitoring()
    configureSerialization()
    configureFrameworks()
    configureHTTP()
    configureMonitoring()
    this.configureSerialization()
    configureDatabases()
    configureFrameworks()
    
    configureHealthRouting()
    configureComputeRouting()
    configureEchoRouting()
    configureIORouting()
}

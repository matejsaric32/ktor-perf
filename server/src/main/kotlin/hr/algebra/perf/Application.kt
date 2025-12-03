package hr.algebra.perf

import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain

fun main(args : Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureDatabases()
    configureFrameworks()
    
    configureHealthRouting()
    configureComputeRouting()
    configureEchoRouting()
    configureIORouting()
    configureNoop()
}

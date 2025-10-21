package hr.algebra.perf

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import hr.algebra.perf.model.HealthResponse

fun Application.configureHealthRouting() {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, HealthResponse())
        }
    }
}
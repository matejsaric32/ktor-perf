package hr.algebra.perf

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureNoop() {
    routing {
        get("/noop") {
            call.respondText("Noop", status = HttpStatusCode.OK)
        }
    }
}
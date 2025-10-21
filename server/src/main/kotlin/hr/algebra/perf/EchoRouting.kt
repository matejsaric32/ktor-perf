package hr.algebra.perf

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import hr.algebra.perf.model.EchoRequest
import hr.algebra.perf.model.EchoResponse

fun Application.configureEchoRouting() {
    routing {
        post("/echo") {
            val startTime = System.currentTimeMillis()
            
            val parseStart = System.currentTimeMillis()
            val echoRequest = call.receive<EchoRequest>()
            val parseTime = System.currentTimeMillis() - parseStart
            
            val serializeStart = System.currentTimeMillis()
            val response = EchoResponse(
                original = echoRequest,
                parseTimeMs = parseTime,
                serializeTimeMs = 0,
                totalTimeMs = 0
            )
            val serializeTime = System.currentTimeMillis() - serializeStart
            
            val totalTime = System.currentTimeMillis() - startTime
            
            
            val finalResponse = response.copy(
                serializeTimeMs = serializeTime,
                totalTimeMs = totalTime
            )
            
            call.respond(HttpStatusCode.OK, finalResponse)
        }
    }
}
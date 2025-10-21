import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import model.HealthResponse

fun Application.configureHealthRouting() {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, HealthResponse())
        }
    }
}
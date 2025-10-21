package hr.algebra.perf

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import hr.algebra.perf.model.ComputeResponse

fun Application.configureComputeRouting() {
    routing {
        get("/compute") {
            val iterations = call.request.queryParameters["iterations"]?.toIntOrNull() ?: 10000
            
            val startTime = System.currentTimeMillis()
            
            var result = 0L
            for (i in 2..iterations) {
                if (isPrime(i)) {
                    result += i
                }
            }
            
            val computeTime = System.currentTimeMillis() - startTime
            
            call.respond(
                HttpStatusCode.OK, ComputeResponse(
                    result = result,
                    iterations = iterations,
                    computeTimeMs = computeTime
                )
            )
        }
    }
}

private fun isPrime(n : Int) : Boolean {
    if (n <= 1) return false
    if (n <= 3) return true
    if (n % 2 == 0 || n % 3 == 0) return false
    
    var i = 5
    while (i * i <= n) {
        if (n % i == 0 || n % (i + 2) == 0) return false
        i += 6
    }
    return true
}
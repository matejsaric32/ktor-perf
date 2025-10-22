package hr.algebra.perf

import hr.algebra.perf.model.IOResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import hr.algebra.perf.model.OrderSummary
import hr.algebra.perf.model.OrderViewedEvent

fun Application.configureIORouting() {
    val databaseConfig = getDatabaseConfig()
    val redisConfig = getRedisConfig()
    val kafkaConfig = getKafkaConfig()
    
    val dbConnection = connectToDatabase(databaseConfig)
    val orderService = OrderService(dbConnection, databaseConfig.schema)
    val redisService = RedisService(redisConfig)
    val kafkaProducer = KafkaProducerService(kafkaConfig)
    
    routing {
        get("/io") {
            val userId = call.request.queryParameters["user_id"]?.toIntOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing user_id parameter")
            
            val startTime = System.currentTimeMillis()
            val metrics = mutableMapOf<String, Long>()
            
            val cacheKey = "order_summary:$userId"
            val redisStart = System.currentTimeMillis()
            val cachedSummary = redisService.getObject<OrderSummary>(cacheKey)
            metrics["redis_get_ms"] = System.currentTimeMillis() - redisStart
            
            val orderSummary = if (cachedSummary != null) {
                metrics["cache_hit"] = 1
                cachedSummary
            } else {
                metrics["cache_hit"] = 0
                
                val dbStart = System.currentTimeMillis()
                val summary = orderService.getOrderSummary(userId)
                metrics["db_query_ms"] = System.currentTimeMillis() - dbStart
                
                val redisSaveStart = System.currentTimeMillis()
                redisService.setObject(cacheKey, summary, ttlSeconds = redisConfig.ttl)
                metrics["redis_set_ms"] = System.currentTimeMillis() - redisSaveStart
                
                summary
            }
            
            val kafkaStart = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                val event = OrderViewedEvent(
                    userId = userId,
                    orderCount = orderSummary.orders.size,
                    totalAmount = orderSummary.orders.sumOf { it.totalAmount }
                )
                kafkaProducer.publishOrderViewed(event)
            }
            metrics["kafka_publish_ms"] = System.currentTimeMillis() - kafkaStart
            
            metrics["total_ms"] = System.currentTimeMillis() - startTime
            
            call.respond(HttpStatusCode.OK, IOResponse(data = orderSummary, metrics = metrics))
        }
    }
}
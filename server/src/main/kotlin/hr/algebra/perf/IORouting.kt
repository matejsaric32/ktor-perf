package hr.algebra.perf

import hr.algebra.perf.model.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

fun Application.configureIORouting() {
    val databaseConfig = getDatabaseConfig()
    val kafkaConfig = getKafkaConfig()
    
    val dbConnection = createHikariDataSource(databaseConfig)
    val orderService = OrderService(dbConnection, databaseConfig.schema)
    val benchmarkService = BenchmarkService(dbConnection, databaseConfig.schema)
    val kafkaProducer = KafkaProducerService(kafkaConfig)
    
    routing {
        get("/io/light") {
            val userId = call.request.queryParameters["user_id"]?.toIntOrNull() ?: return@get call.respond(
                HttpStatusCode.BadRequest, "Missing user_id parameter"
            )
            
            val startTime = System.currentTimeMillis()
            
            val dbStart = System.currentTimeMillis()
            val orderSummary = orderService.getOrderSummary(userId)
            val dbQueryMs = System.currentTimeMillis() - dbStart
            
            val kafkaStart = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                kafkaProducer.publishOrderViewed(
                    OrderViewedEvent(
                    userId = userId,
                    orderCount = orderSummary.orders.size,
                    totalAmount = orderSummary.orders.sumOf { it.totalAmount }))
            }
            val kafkaPublishMs = System.currentTimeMillis() - kafkaStart
            
            val totalMs = System.currentTimeMillis() - startTime
            
            val response = IOLightResponse(
                data = orderSummary, metrics = IOMetrics(
                    redisGetMs = 0,
                    cacheHit = false,
                    dbQueryMs = dbQueryMs,
                    kafkaPublishMs = kafkaPublishMs,
                    totalMs = totalMs,
                    workload = "light",
                    operations = 2
                )
            )
            
            call.respond(HttpStatusCode.OK, response)
        }
        
        get("/io/heavy") {
            val userId = call.request.queryParameters["user_id"]?.toIntOrNull() ?: return@get call.respond(
                HttpStatusCode.BadRequest, "Missing user_id parameter"
            )
            
            val startTime = System.currentTimeMillis()
            val metrics = mutableMapOf<String, Any>()
            val operationCount = mutableListOf<String>()
            
            val (slowResults, slowQueryTime) = benchmarkService.slowQueryUnindexedSessionId(
                "session-${userId % 10}", 50
            )
            metrics["slow_query_ms"] = slowQueryTime
            metrics["slow_query_results"] = slowResults.size
            operationCount.add("slow_unindexed_query")
            
            val (fastResults, fastQueryTime) = benchmarkService.fastQueryIndexed(userId, 100)
            metrics["fast_query_ms"] = fastQueryTime
            metrics["fast_query_results"] = fastResults.size
            operationCount.add("fast_indexed_query")
            
            val dbStart = System.currentTimeMillis()
            val orderSummary = orderService.getOrderSummary(userId)
            metrics["order_query_ms"] = System.currentTimeMillis() - dbStart
            operationCount.add("order_summary_query")
            
            val statsStart = System.currentTimeMillis()
            val userStats = orderService.getUserStats(userId)
            metrics["stats_query_ms"] = System.currentTimeMillis() - statsStart
            operationCount.add("stats_aggregation")
            
            val (aggData, aggTime) = benchmarkService.slowQueryUnindexedActionType("purchase")
            metrics["complex_aggregation_ms"] = aggTime
            operationCount.add("complex_aggregation")
            
            val kafkaStart = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                kafkaProducer.publishOrderViewed(
                    OrderViewedEvent(
                    userId = userId,
                    orderCount = orderSummary.orders.size,
                    totalAmount = orderSummary.orders.sumOf { it.totalAmount }))
                
                repeat(4) { i ->
                    kafkaProducer.publishOrderViewed(
                        OrderViewedEvent(
                            userId = userId, orderCount = i + 1, totalAmount = (i + 1) * 100.0
                        )
                    )
                }
            }
            metrics["kafka_events"] = 5
            metrics["kafka_total_ms"] = System.currentTimeMillis() - kafkaStart
            operationCount.add("kafka_5x_events")
            
            val totalTime = System.currentTimeMillis() - startTime
            metrics["total_ms"] = totalTime
            metrics["workload"] = "heavy"
            metrics["total_operations"] = operationCount.size
            metrics["operations_list"] = operationCount
            
            val response = IOHeavyResponse(
                data = IOHeavyData(
                    orderSummary = orderSummary,
                    userStats = userStats,
                    slowQueryResults = slowResults.size,
                    fastQueryResults = fastResults.size,
                    aggregationData = aggData.toJsonElement() as JsonObject
                ), metrics = metrics.mapValues { it.value }.toJsonElement() as JsonObject
            )
            
            call.respond(HttpStatusCode.OK, response)
        }
        
        post("/io/heavy/write") {
            val userId = call.request.queryParameters["user_id"]?.toIntOrNull() ?: 1
            val bulkCount = call.request.queryParameters["bulk_count"]?.toIntOrNull() ?: 100
            
            val startTime = System.currentTimeMillis()
            val metrics = mutableMapOf<String, String>()
            
            val orderStart = System.currentTimeMillis()
            val orderRequest = CreateOrderRequest(
                userId = userId, totalAmount = 100.0 + (userId * 10), status = "pending", items = listOf(
                    OrderItem("Product-A", 1, 50.0), OrderItem("Product-B", 2, 25.0)
                )
            )
            val orderId = orderService.createOrder(orderRequest)
            metrics["order_insert_ms"] = (System.currentTimeMillis() - orderStart).toString()
            
            val (insertedCount, bulkInsertTime) = benchmarkService.bulkInsertEvents(bulkCount, "heavy_write_test")
            metrics["bulk_insert_count"] = insertedCount.toString()
            metrics["bulk_insert_ms"] = bulkInsertTime.toString()
            
            val (selectCount, insertSelectTime) = benchmarkService.insertAndSelect("immediate_read_test")
            metrics["insert_select_ms"] = insertSelectTime.toString()
            metrics["select_result_count"] = selectCount.toString()
            
            val kafkaStart = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                repeat(10) { i ->
                    kafkaProducer.publishOrderViewed(
                        OrderViewedEvent(
                            userId = userId,
                            orderCount = i + 1,
                            totalAmount = (i + 1) * 50.0,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
            metrics["kafka_events"] = "10"
            metrics["kafka_total_ms"] = (System.currentTimeMillis() - kafkaStart).toString()
            
            val totalTime = System.currentTimeMillis() - startTime
            metrics["total_ms"] = totalTime.toString()
            metrics["workload"] = "heavy_write"
            metrics["total_operations"] = "4"
            
            val response = IOHeavyWriteResponse(
                result = WriteResult(
                    orderId = orderId, bulkInserted = insertedCount, userId = userId
                ), metrics = metrics.mapValues { it.value as Any }.toJsonElement() as JsonObject
            )
            
            call.respond(HttpStatusCode.Created, response)
        }
        
        get("/io/stress") {
            val userId = call.request.queryParameters["user_id"]?.toIntOrNull() ?: 1
            
            val startTime = System.currentTimeMillis()
            val metrics = mutableMapOf<String, String>()
            
            val heavyReadStart = System.currentTimeMillis()
            
            withContext(Dispatchers.IO) {
                val slowQuery = async { benchmarkService.slowQueryUnindexedSessionId("session-", 30) }
                val fastQuery = async { benchmarkService.fastQueryIndexed(userId, 50) }
                val orderQuery = async { orderService.getOrderSummary(userId) }
                val statsQuery = async { orderService.getUserStats(userId) }
                
                val (slow, slowTime) = slowQuery.await()
                val (fast, fastTime) = fastQuery.await()
                orderQuery.await()
                statsQuery.await()
                
                metrics["parallel_slow_ms"] = slowTime.toString()
                metrics["parallel_fast_ms"] = fastTime.toString()
                metrics["parallel_results"] = (slow.size + fast.size).toString()
            }
            metrics["parallel_queries_ms"] = (System.currentTimeMillis() - heavyReadStart).toString()
            
            val (inserted, insertTime) = benchmarkService.bulkInsertEvents(50, "stress_test")
            metrics["bulk_insert_ms"] = insertTime.toString()
            metrics["bulk_inserted"] = inserted.toString()
            
            val (_, aggTime) = benchmarkService.slowQueryComplexUnindexed(200, 500000)
            metrics["complex_agg_ms"] = aggTime.toString()
            
            val kafkaStart = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                repeat(20) { i ->
                    kafkaProducer.publishOrderViewed(
                        OrderViewedEvent(
                            userId = userId + i, orderCount = i, totalAmount = i * 100.0
                        )
                    )
                }
            }
            metrics["kafka_20x_ms"] = (System.currentTimeMillis() - kafkaStart).toString()
            
            val totalTime = System.currentTimeMillis() - startTime
            metrics["total_ms"] = totalTime.toString()
            metrics["workload"] = "stress_test"
            
            val response = IOStressResponse(
                test = "comprehensive_stress_test",
                metrics = metrics.mapValues { it.value as Any }.toJsonElement() as JsonObject
            )
            
            call.respond(HttpStatusCode.OK, response)
        }
        
        get("/io/status") {
            val benchmarkRows = benchmarkService.getTotalRowCount()
            
            call.respond(
                HttpStatusCode.OK, mapOf(
                    "status" to "operational", "services" to mapOf(
                        "database" to "connected", "kafka" to "connected"
                    ), "benchmark_data" to mapOf(
                        "total_rows" to benchmarkRows, "ready" to (benchmarkRows > 0)
                    ), "endpoints" to mapOf(
                        "light" to "/io/light (1 DB + 1 Kafka)",
                        "heavy" to "/io/heavy (5 DB queries + 5 Kafka)",
                        "heavy_write" to "/io/heavy/write (3 DB writes + 10 Kafka)",
                        "stress" to "/io/stress (parallel queries + bulk ops + 20 Kafka)"
                    )
                )
            )
        }
    }
}

private fun Map<String, Any>.toJsonElement() : JsonElement = buildJsonObject {
    this@toJsonElement.forEach { (key, value) ->
        when (value) {
            is String -> put(key, value)
            is Number -> put(key, value)
            is Boolean -> put(key, value)
            is Map<*, *> -> put(key, (value as Map<String, Any>).toJsonElement())
            is List<*> -> put(key, JsonArray(value.map {
                when (it) {
                    is String -> JsonPrimitive(it)
                    is Number -> JsonPrimitive(it)
                    is Boolean -> JsonPrimitive(it)
                    is Map<*, *> -> (it as Map<String, Any>).toJsonElement()
                    else -> JsonPrimitive(it.toString())
                }
            }))
            
            null -> put(key, JsonNull)
            else -> put(key, value.toString())
        }
    }
}
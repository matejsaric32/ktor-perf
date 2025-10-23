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
    val redisConfig = getRedisConfig()
    val kafkaConfig = getKafkaConfig()
    
    val dbConnection = connectToDatabase(databaseConfig)
    val orderService = OrderService(dbConnection, databaseConfig.schema)
    val benchmarkService = BenchmarkService(dbConnection, databaseConfig.schema)
    val redisService = RedisService(redisConfig)
    val kafkaProducer = KafkaProducerService(kafkaConfig)
    
    routing {
        get("/io/light") {
            val userId = call.request.queryParameters["user_id"]?.toIntOrNull() ?: return@get call.respond(
                HttpStatusCode.BadRequest, "Missing user_id parameter"
            )
            
            val startTime = System.currentTimeMillis()
            
            val cacheKey = "order_summary:$userId"
            val redisStart = System.currentTimeMillis()
            val cachedSummary = redisService.getObject<OrderSummary>(cacheKey)
            val redisGetMs = System.currentTimeMillis() - redisStart
            
            val dbStart = System.currentTimeMillis()
            val orderSummary = cachedSummary ?: orderService.getOrderSummary(userId).also {
                redisService.setObject(cacheKey, it, ttlSeconds = redisConfig.ttl)
            }
            val dbQueryMs = if (cachedSummary != null) 0L else System.currentTimeMillis() - dbStart
            
            val kafkaStart = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                kafkaProducer.publishOrderViewed(
                    OrderViewedEvent(
                        userId = userId,
                        orderCount = orderSummary.orders.size,
                        totalAmount = orderSummary.orders.sumOf { it.totalAmount })
                )
            }
            val kafkaPublishMs = System.currentTimeMillis() - kafkaStart
            
            val totalMs = System.currentTimeMillis() - startTime
            
            val response = IOLightResponse(
                data = orderSummary, metrics = IOMetrics(
                    redisGetMs = redisGetMs,
                    cacheHit = cachedSummary != null,
                    dbQueryMs = dbQueryMs,
                    kafkaPublishMs = kafkaPublishMs,
                    totalMs = totalMs,
                    workload = "light",
                    operations = 3
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
            
            val redisStart = System.currentTimeMillis()
            val cacheKey1 = "order_summary:$userId"
            val cacheKey2 = "user_stats:$userId"
            val cacheKey3 = "user_meta:$userId"
            
            val cached1 = redisService.get(cacheKey1)
            val cached2 = redisService.get(cacheKey2)
            val cached3 = redisService.get(cacheKey3)
            metrics["redis_operations"] = 3
            metrics["redis_total_ms"] = System.currentTimeMillis() - redisStart
            operationCount.add("redis_3x")
            
            val slowQueryStart = System.currentTimeMillis()
            val (slowResults, slowQueryTime) = benchmarkService.slowQueryUnindexedSessionId(
                "session-${userId % 10}", 50
            )
            metrics["slow_query_ms"] = slowQueryTime
            metrics["slow_query_results"] = slowResults.size
            operationCount.add("slow_unindexed_query")
            
            val fastQueryStart = System.currentTimeMillis()
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
            
            val aggStart = System.currentTimeMillis()
            val (aggData, aggTime) = benchmarkService.slowQueryUnindexedActionType("purchase")
            metrics["complex_aggregation_ms"] = aggTime
            operationCount.add("complex_aggregation")
            
            val kafkaStart = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                kafkaProducer.publishOrderViewed(
                    OrderViewedEvent(
                        userId = userId,
                        orderCount = orderSummary.orders.size,
                        totalAmount = orderSummary.orders.sumOf { it.totalAmount })
                )
                
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
            
            val cacheWriteStart = System.currentTimeMillis()
            redisService.setObject(cacheKey1, orderSummary, ttlSeconds = redisConfig.ttl)
            redisService.set(cacheKey2, redisService.json.encodeToString(userStats), ttlSeconds = redisConfig.ttl)
            redisService.set(
                cacheKey3, """{"processed": true, "timestamp": ${System.currentTimeMillis()}}""", ttlSeconds = 60
            )
            metrics["cache_writes"] = 3
            metrics["cache_write_ms"] = System.currentTimeMillis() - cacheWriteStart
            operationCount.add("redis_3x_writes")
            
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
                ),
                metrics = metrics.mapValues { it.value }.toJsonElement() as JsonObject
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
                userId = userId,
                totalAmount = 100.0 + (userId * 10),
                status = "pending",
                items = listOf(
                    OrderItem("Product-A", 1, 50.0),
                    OrderItem("Product-B", 2, 25.0)
                )
            )
            val orderId = orderService.createOrder(orderRequest)
            metrics["order_insert_ms"] = (System.currentTimeMillis() - orderStart).toString()
            
            val bulkStart = System.currentTimeMillis()
            val (insertedCount, bulkInsertTime) = benchmarkService.bulkInsertEvents(bulkCount, "heavy_write_test")
            metrics["bulk_insert_count"] = insertedCount.toString()
            metrics["bulk_insert_ms"] = bulkInsertTime.toString()
            
            val insertSelectStart = System.currentTimeMillis()
            val (selectCount, insertSelectTime) = benchmarkService.insertAndSelect("immediate_read_test")
            metrics["insert_select_ms"] = insertSelectTime.toString()
            metrics["select_result_count"] = selectCount.toString()
            
            val cacheInvalidateStart = System.currentTimeMillis()
            redisService.delete("order_summary:$userId")
            redisService.delete("user_stats:$userId")
            redisService.delete("user_meta:$userId")
            redisService.deletePattern("benchmark:*")
            metrics["cache_invalidations"] = "4"
            metrics["cache_invalidate_ms"] = (System.currentTimeMillis() - cacheInvalidateStart).toString()
            
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
            metrics["total_operations"] = "5"
            
            val response = IOHeavyWriteResponse(
                result = WriteResult(
                    orderId = orderId,
                    bulkInserted = insertedCount,
                    userId = userId
                ),
                metrics = metrics.mapValues { it.value as Any }.toJsonElement() as JsonObject
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
                val orders = orderQuery.await()
                val stats = statsQuery.await()
                
                metrics["parallel_slow_ms"] = slowTime.toString()
                metrics["parallel_fast_ms"] = fastTime.toString()
                metrics["parallel_results"] = (slow.size + fast.size).toString()
            }
            metrics["parallel_queries_ms"] = (System.currentTimeMillis() - heavyReadStart).toString()
            
            val bulkStart = System.currentTimeMillis()
            val (inserted, insertTime) = benchmarkService.bulkInsertEvents(50, "stress_test")
            metrics["bulk_insert_ms"] = insertTime.toString()
            metrics["bulk_inserted"] = inserted.toString()
            
            val aggStart = System.currentTimeMillis()
            val (aggData, aggTime) = benchmarkService.slowQueryComplexUnindexed(200, 500000)
            metrics["complex_agg_ms"] = aggTime.toString()
            
            val kafkaStart = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                repeat(20) { i ->
                    kafkaProducer.publishOrderViewed(
                        OrderViewedEvent(
                            userId = userId + i,
                            orderCount = i,
                            totalAmount = i * 100.0
                        )
                    )
                }
            }
            metrics["kafka_20x_ms"] = (System.currentTimeMillis() - kafkaStart).toString()
            
            val cacheStart = System.currentTimeMillis()
            repeat(10) { i ->
                redisService.set("stress_key_$i", "value_$i", 60)
                redisService.get("stress_key_$i")
            }
            metrics["cache_20x_ops_ms"] = (System.currentTimeMillis() - cacheStart).toString()
            
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
                        "database" to "connected", "redis" to "connected", "kafka" to "connected"
                    ), "benchmark_data" to mapOf(
                        "total_rows" to benchmarkRows, "ready" to (benchmarkRows > 0)
                    ), "endpoints" to mapOf(
                        "light" to "/io/light (1 DB + 1 Redis + 1 Kafka)",
                        "heavy" to "/io/heavy (6 DB queries + 8 Redis + 5 Kafka)",
                        "heavy_write" to "/io/heavy/write (3 DB writes + 4 cache ops + 10 Kafka)",
                        "stress" to "/io/stress (parallel queries + bulk ops + heavy events)"
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
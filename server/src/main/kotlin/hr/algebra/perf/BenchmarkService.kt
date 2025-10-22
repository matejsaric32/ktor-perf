package hr.algebra.perf

import hr.algebra.perf.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection

class BenchmarkService(
    private val connection : Connection,
    private val schema : String = "perf"
) {
    
    suspend fun slowQueryUnindexedSessionId(
        sessionPattern : String,
        limit : Int = 100
    ) : Pair<List<BenchmarkLog>, Long> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            val query = """
                SELECT id, user_id, session_id, action_type, payload, ip_address, random_value, status_code
                FROM $schema.benchmark_logs
                WHERE session_id LIKE ?
                LIMIT ?
            """
            
            val statement = connection.prepareStatement(query)
            statement.setString(1, "$sessionPattern%")
            statement.setInt(2, limit)
            
            val resultSet = statement.executeQuery()
            val logs = mutableListOf<BenchmarkLog>()
            
            while (resultSet.next()) {
                logs.add(
                    BenchmarkLog(
                        id = resultSet.getInt("id"),
                        userId = resultSet.getInt("user_id"),
                        sessionId = resultSet.getString("session_id"),
                        actionType = resultSet.getString("action_type"),
                        payload = resultSet.getString("payload"),
                        ipAddress = resultSet.getString("ip_address"),
                        randomValue = resultSet.getInt("random_value"),
                        statusCode = resultSet.getInt("status_code")
                    )
                )
            }
            
            val queryTime = System.currentTimeMillis() - startTime
            Pair(logs, queryTime)
        }
    
    suspend fun slowQueryUnindexedActionType(actionType : String) : Pair<Map<String, Any>, Long> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            val query = """
                SELECT
                    COUNT(*) as total_count,
                    AVG(random_value) as avg_random_value,
                    MIN(random_value) as min_random_value,
                    MAX(random_value) as max_random_value,
                    COUNT(DISTINCT user_id) as unique_users
                FROM $schema.benchmark_logs
                WHERE action_type = ?
            """
            
            val statement = connection.prepareStatement(query)
            statement.setString(1, actionType)
            
            val resultSet = statement.executeQuery()
            val result = mutableMapOf<String, Any>()
            
            if (resultSet.next()) {
                result["total_count"] = resultSet.getInt("total_count")
                result["avg_random_value"] = resultSet.getDouble("avg_random_value")
                result["min_random_value"] = resultSet.getInt("min_random_value")
                result["max_random_value"] = resultSet.getInt("max_random_value")
                result["unique_users"] = resultSet.getInt("unique_users")
            }
            
            val queryTime = System.currentTimeMillis() - startTime
            Pair(result, queryTime)
        }
    
    suspend fun slowQueryComplexUnindexed(statusCode : Int, minRandomValue : Int) : Pair<Map<String, Any>, Long> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            val query = """
                SELECT
                    action_type,
                    COUNT(*) as count,
                    AVG(random_value) as avg_value,
                    STRING_AGG(DISTINCT ip_address, ', ') as ip_addresses
                FROM $schema.benchmark_logs
                WHERE status_code = ?
                  AND random_value > ?
                  AND LENGTH(payload) > 20
                GROUP BY action_type
                ORDER BY count DESC
            """
            
            val statement = connection.prepareStatement(query)
            statement.setInt(1, statusCode)
            statement.setInt(2, minRandomValue)
            
            val resultSet = statement.executeQuery()
            val results = mutableListOf<Map<String, Any>>()
            
            while (resultSet.next()) {
                results.add(
                    mapOf(
                        "action_type" to resultSet.getString("action_type"),
                        "count" to resultSet.getInt("count"),
                        "avg_value" to resultSet.getDouble("avg_value"),
                        "ip_addresses" to (resultSet.getString("ip_addresses") ?: "")
                    )
                )
            }
            
            val queryTime = System.currentTimeMillis() - startTime
            Pair(mapOf("results" to results, "result_count" to results.size), queryTime)
        }
    
    suspend fun fastQueryIndexed(userId : Int, limit : Int = 100) : Pair<List<BenchmarkLog>, Long> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            val query = """
                SELECT id, user_id, session_id, action_type, payload, ip_address, random_value, status_code
                FROM $schema.benchmark_logs
                WHERE user_id = ?
                ORDER BY created_at DESC
                LIMIT ?
            """
            
            val statement = connection.prepareStatement(query)
            statement.setInt(1, userId)
            statement.setInt(2, limit)
            
            val resultSet = statement.executeQuery()
            val logs = mutableListOf<BenchmarkLog>()
            
            while (resultSet.next()) {
                logs.add(
                    BenchmarkLog(
                        id = resultSet.getInt("id"),
                        userId = resultSet.getInt("user_id"),
                        sessionId = resultSet.getString("session_id"),
                        actionType = resultSet.getString("action_type"),
                        payload = resultSet.getString("payload"),
                        ipAddress = resultSet.getString("ip_address"),
                        randomValue = resultSet.getInt("random_value"),
                        statusCode = resultSet.getInt("status_code")
                    )
                )
            }
            
            val queryTime = System.currentTimeMillis() - startTime
            Pair(logs, queryTime)
        }
    
    suspend fun bulkInsertEvents(count : Int, eventType : String) : Pair<Int, Long> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            connection.autoCommit = false
            
            try {
                val query = """
                    INSERT INTO $schema.benchmark_events (event_type, event_data, source_system, correlation_id)
                    VALUES (?, ?::jsonb, ?, ?)
                """
                
                val statement = connection.prepareStatement(query)
                
                for (i in 1..count) {
                    statement.setString(1, eventType)
                    statement.setString(2, """{"index": $i, "timestamp": ${System.currentTimeMillis()}}""")
                    statement.setString(3, "benchmark-system")
                    statement.setString(4, "correlation-${System.currentTimeMillis()}-$i")
                    statement.addBatch()
                    
                    if (i % 100 == 0) {
                        statement.executeBatch()
                    }
                }
                
                statement.executeBatch()
                connection.commit()
                
                val insertTime = System.currentTimeMillis() - startTime
                Pair(count, insertTime)
            } catch (e : Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    
    suspend fun insertAndSelect(eventType : String) : Pair<Int, Long> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            val insertQuery = """
                INSERT INTO $schema.benchmark_events (event_type, event_data, source_system, correlation_id)
                VALUES (?, ?::jsonb, ?, ?)
                RETURNING id
            """
            
            val insertStmt = connection.prepareStatement(insertQuery)
            val correlationId = "correlation-${System.currentTimeMillis()}"
            insertStmt.setString(1, eventType)
            insertStmt.setString(2, """{"timestamp": ${System.currentTimeMillis()}}""")
            insertStmt.setString(3, "benchmark-system")
            insertStmt.setString(4, correlationId)
            
            val resultSet = insertStmt.executeQuery()
            val insertedId = if (resultSet.next()) resultSet.getInt(1) else 0
            
            val selectQuery = """
                SELECT COUNT(*) FROM $schema.benchmark_events WHERE event_type = ?
            """
            
            val selectStmt = connection.prepareStatement(selectQuery)
            selectStmt.setString(1, eventType)
            val selectResult = selectStmt.executeQuery()
            
            val count = if (selectResult.next()) selectResult.getInt(1) else 0
            
            val totalTime = System.currentTimeMillis() - startTime
            Pair(count, totalTime)
        }
    
    suspend fun getTotalRowCount() : Int = withContext(Dispatchers.IO) {
        val query = "SELECT COUNT(*) FROM $schema.benchmark_logs"
        val statement = connection.createStatement()
        val resultSet = statement.executeQuery(query)
        
        if (resultSet.next()) {
            resultSet.getInt(1)
        } else {
            0
        }
    }
}
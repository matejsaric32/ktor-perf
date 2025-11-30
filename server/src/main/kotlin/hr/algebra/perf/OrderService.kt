package hr.algebra.perf

import hr.algebra.perf.model.CreateOrderRequest
import hr.algebra.perf.model.Order
import hr.algebra.perf.model.OrderSummary
import hr.algebra.perf.model.UserStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Statement
import java.sql.Timestamp
import javax.sql.DataSource

class OrderService(
    private val dataSource : DataSource,
    private val schema : String = "perf"
) {
    private val selectOrderSummary = """
        SELECT
            o.id,
            o.total_amount,
            o.status,
            COUNT(oi.id) as item_count
        FROM $schema.orders o
        LEFT JOIN $schema.order_items oi ON o.id = oi.order_id
        WHERE o.user_id = ?
          AND o.status IN ('pending', 'completed')
        GROUP BY o.id, o.total_amount, o.status
        ORDER BY o.created_at DESC
        LIMIT 5
    """
    
    suspend fun getOrderSummary(userId : Int) : OrderSummary = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(selectOrderSummary)
            statement.setInt(1, userId)
            val resultSet = statement.executeQuery()
            
            val orders = mutableListOf<Order>()
            while (resultSet.next()) {
                orders.add(
                    Order(
                        id = resultSet.getInt("id"),
                        totalAmount = resultSet.getDouble("total_amount"),
                        status = resultSet.getString("status"),
                        itemCount = resultSet.getInt("item_count")
                    )
                )
            }
            
            OrderSummary(userId, orders)
        }
    }
    
    suspend fun getUserStats(userId : Int) : UserStats = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            val query = """
                SELECT
                    COUNT(*) as total_orders,
                    COALESCE(SUM(total_amount), 0) as total_spent,
                    COALESCE(AVG(total_amount), 0) as avg_order_value,
                    MAX(created_at) as last_order_date
                FROM $schema.orders
                WHERE user_id = ?
            """
            
            val statement = connection.prepareStatement(query)
            statement.setInt(1, userId)
            val resultSet = statement.executeQuery()
            
            if (resultSet.next()) {
                val lastOrderDate = resultSet.getTimestamp("last_order_date")
                UserStats(
                    userId = userId,
                    totalOrders = resultSet.getInt("total_orders"),
                    totalSpent = resultSet.getDouble("total_spent"),
                    averageOrderValue = resultSet.getDouble("avg_order_value"),
                    lastOrderDate = lastOrderDate?.time
                )
            } else {
                UserStats(
                    userId = userId,
                    totalOrders = 0,
                    totalSpent = 0.0,
                    averageOrderValue = 0.0,
                    lastOrderDate = null
                )
            }
        }
    }
    
    suspend fun createOrder(request : CreateOrderRequest) : Int = withContext(Dispatchers.IO) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            
            try {
                val insertOrderQuery = """
                    INSERT INTO $schema.orders (user_id, total_amount, status, created_at)
                    VALUES (?, ?, ?, ?)
                """
                
                val orderStatement = connection.prepareStatement(insertOrderQuery, Statement.RETURN_GENERATED_KEYS)
                orderStatement.setInt(1, request.userId)
                orderStatement.setDouble(2, request.totalAmount)
                orderStatement.setString(3, request.status)
                orderStatement.setTimestamp(4, Timestamp(System.currentTimeMillis()))
                orderStatement.executeUpdate()
                
                val generatedKeys = orderStatement.generatedKeys
                val orderId = if (generatedKeys.next()) {
                    generatedKeys.getInt(1)
                } else {
                    throw Exception("Failed to create order - no ID generated")
                }
                
                val insertItemQuery = """
                    INSERT INTO $schema.order_items (order_id, product_name, quantity, price)
                    VALUES (?, ?, ?, ?)
                """
                
                val itemStatement = connection.prepareStatement(insertItemQuery)
                for (item in request.items) {
                    itemStatement.setInt(1, orderId)
                    itemStatement.setString(2, item.productName)
                    itemStatement.setInt(3, item.quantity)
                    itemStatement.setDouble(4, item.price)
                    itemStatement.addBatch()
                }
                itemStatement.executeBatch()
                
                connection.commit()
                orderId
            } catch (e : Exception) {
                connection.rollback()
                throw e
            }
        }
    }
}
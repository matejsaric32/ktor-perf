import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import model.Order
import model.OrderSummary
import org.intellij.lang.annotations.Language
import java.sql.Connection

class OrderService(private val connection : Connection) {
    companion object {
        @Language("SQL")
        private const val SELECT_ORDER_SUMMARY = """
            SELECT
                o.id,
                o.total_amount,
                o.status,
                COUNT(oi.id) as item_count
            FROM orders o
            LEFT JOIN order_items oi ON o.id = oi.order_id
            WHERE o.user_id = ?
              AND o.status IN ('pending', 'completed')
            GROUP BY o.id, o.total_amount, o.status
            ORDER BY o.created_at DESC
            LIMIT 5
        """
    }
    
    suspend fun getOrderSummary(userId : Int) : OrderSummary = withContext(Dispatchers.IO) {
        val statement = connection.prepareStatement(SELECT_ORDER_SUMMARY)
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
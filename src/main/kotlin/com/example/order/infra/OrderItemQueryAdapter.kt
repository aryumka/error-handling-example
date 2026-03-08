package com.example.order.infra

import com.example.order.domain.OrderId
import com.example.order.domain.OrderItem
import com.example.order.port.OrderItemQueryPort
import org.springframework.stereotype.Component

@Component
class OrderItemQueryAdapter(
    private val orderItemRepository: OrderItemRepository,
) : OrderItemQueryPort {

    override fun getItems(orderId: OrderId): List<OrderItem> {
        val entities = orderItemRepository.findAllByOrderId(orderId.value)
        return entities.map { OrderItem(orderId, it.productName, it.quantity) }
    }
}

interface OrderItemRepository {
    fun findAllByOrderId(orderId: Long): List<OrderItemEntity>
}

data class OrderItemEntity(
    val orderId: Long,
    val productName: String,
    val quantity: Int,
)

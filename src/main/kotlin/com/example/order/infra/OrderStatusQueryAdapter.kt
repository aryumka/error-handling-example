package com.example.order.infra

import com.example.core.InfraException
import com.example.order.domain.OrderId
import com.example.order.domain.OrderProgress
import com.example.order.domain.OrderStatus
import com.example.order.port.OrderStatusQueryPort
import org.springframework.stereotype.Component

@Component
class OrderStatusQueryAdapter(
    private val orderProgressRepository: OrderProgressRepository,
) : OrderStatusQueryPort {

    override fun getProgress(orderId: OrderId): OrderProgress {
        val entity = orderProgressRepository.findByOrderId(orderId.value)

        return OrderProgress(
            orderId = orderId,
            status = entity?.let {
                try {
                    OrderStatus.valueOf(it.status)
                } catch (e: IllegalArgumentException) {
                    throw InfraException("알 수 없는 주문 상태: ${it.status}", module = "order")
                }
            } ?: OrderStatus.CREATED,
        )
    }
}

interface OrderProgressRepository {
    fun findByOrderId(orderId: Long): OrderProgressEntity?
}

data class OrderProgressEntity(
    val orderId: Long,
    val status: String,
)

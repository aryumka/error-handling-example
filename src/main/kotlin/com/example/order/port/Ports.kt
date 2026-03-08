package com.example.order.port

import com.example.core.Result
import com.example.order.domain.*

// === Query Ports ===

interface CustomerQueryPort {
    fun getCustomer(customerId: CustomerId): Customer?
}

interface OrderStatusQueryPort {
    fun getProgress(orderId: OrderId): OrderProgress
}

interface OrderItemQueryPort {
    fun getItems(orderId: OrderId): List<OrderItem>
}

// === Command Ports ===

interface PaymentExecutionPort {
    fun execute(orderId: OrderId, items: List<OrderItem>): Result<PaymentExecutionException, Unit>
}

interface AlertPublisher {
    fun send(message: String)
}

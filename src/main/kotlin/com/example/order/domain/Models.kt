package com.example.order.domain

// === Value Objects ===

data class CustomerId(val value: Long)
data class OrderId(val value: Long)

// === Entities ===

data class Customer(
    val customerId: CustomerId,
    val name: String,
    val isActive: Boolean,
)

enum class OrderStatus { CREATED, CONFIRMED, PROCESSING, COMPLETED, CANCELLED }

data class OrderProgress(
    val orderId: OrderId,
    val status: OrderStatus,
) {
    val isOrderable: Boolean get() = status == OrderStatus.CREATED || status == OrderStatus.CANCELLED
}

data class OrderItem(
    val orderId: OrderId,
    val productName: String,
    val quantity: Int,
)

// === Request / Event ===

data class PlaceOrderRequest(
    val customerId: CustomerId,
    val orderId: OrderId,
)

data class OrderCompletedEvent(
    val customerId: CustomerId,
    val orderId: OrderId,
    val itemCount: Int,
)

package com.example.order.port

import com.example.core.Result
import com.example.core.asFailure
import com.example.core.asSuccess
import com.example.order.domain.*

// === Query Port Fakes ===

class FakeCustomerQueryPort : CustomerQueryPort {
    var returnNull = false
    var isActive = true

    override fun getCustomer(customerId: CustomerId): Customer? {
        if (returnNull) return null
        return Customer(
            customerId = customerId,
            name = "홍길동",
            isActive = isActive,
        )
    }
}

class FakeOrderStatusQueryPort : OrderStatusQueryPort {
    var status: OrderStatus = OrderStatus.CREATED

    override fun getProgress(orderId: OrderId): OrderProgress {
        return OrderProgress(orderId = orderId, status = status)
    }
}

class FakeOrderItemQueryPort : OrderItemQueryPort {
    var returnEmpty = false

    override fun getItems(orderId: OrderId): List<OrderItem> {
        if (returnEmpty) return emptyList()
        return listOf(
            OrderItem(orderId, "노트북", 1),
            OrderItem(orderId, "마우스", 2),
        )
    }
}

// === Command Port Fakes ===

class FakePaymentExecutionPort : PaymentExecutionPort {
    var failWith: PaymentExecutionException? = null
    val executedItems = mutableListOf<List<OrderItem>>()

    override fun execute(orderId: OrderId, items: List<OrderItem>): Result<PaymentExecutionException, Unit> {
        failWith?.let { return it.asFailure() }
        executedItems.add(items)
        return Unit.asSuccess()
    }
}

class FakeAlertPublisher : AlertPublisher {
    val sentMessages = mutableListOf<String>()

    override fun send(message: String) {
        sentMessages.add(message)
    }
}

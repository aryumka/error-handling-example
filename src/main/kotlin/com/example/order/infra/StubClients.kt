package com.example.order.infra

import org.springframework.stereotype.Component

/**
 * 예제 앱 부팅용 스텁 구현체.
 * 실제 환경에서는 HTTP 클라이언트, JPA 레포지토리 등으로 교체된다.
 */
@Component
class StubPaymentClient : PaymentClient {
    override fun getCustomerResponse(customerId: Long): CustomerResponse {
        return CustomerResponse(
            name = "홍길동",
            isActive = true,
        )
    }

    override fun requestPayment(orderId: Long, productNames: List<String>) {
        // stub: 성공 처리
    }
}

@Component
class StubOrderProgressRepository : OrderProgressRepository {
    override fun findByOrderId(orderId: Long): OrderProgressEntity? {
        return null // CREATED 상태로 처리
    }
}

@Component
class StubOrderItemRepository : OrderItemRepository {
    override fun findAllByOrderId(orderId: Long): List<OrderItemEntity> {
        return listOf(
            OrderItemEntity(orderId, "노트북", 1),
            OrderItemEntity(orderId, "마우스", 2),
        )
    }
}

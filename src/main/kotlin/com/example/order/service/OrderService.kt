package com.example.order.service

import com.example.core.*
import com.example.order.domain.*
import com.example.order.port.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class OrderService(
    private val customerQueryPort: CustomerQueryPort,
    private val orderStatusQueryPort: OrderStatusQueryPort,
    private val orderItemQueryPort: OrderItemQueryPort,
    private val paymentExecutionPort: PaymentExecutionPort,
    @param:Value("\${order.auto-order-enabled:true}") private val autoOrderEnabled: Boolean = true,
) {

    fun placeOrder(request: PlaceOrderRequest): Result<BusinessError, OrderCompletedEvent> = resultOf {
        // 1. 고객 검증
        val customer = ensureNotNull(customerQueryPort.getCustomer(request.customerId)) { CustomerNotFoundError() }
        ensure(customer.isActive) { CustomerNotFoundError() }

        // 2. 주문 상태 확인
        val progress = orderStatusQueryPort.getProgress(request.orderId)
        ensure(progress.isOrderable) { OrderNotReadyError(progress.status.name) }

        // 3. 기능 활성화 확인
        ensure(autoOrderEnabled) { FeatureDisabledError("자동 주문") }

        // 4. 주문 상품 조회
        val items = orderItemQueryPort.getItems(request.orderId)
        ensure(items.isNotEmpty()) { EmptyCartError() }

        // 5. 결제 실행
        // 실패를 값(Result)으로 다루기 때문에 에러별 후속 처리를 체이닝으로 합성할 수 있다.
        // throw 방식에서는 try-catch 중첩으로만 가능한 흐름.
        paymentExecutionPort.execute(request.orderId, items)
            .retry(PaymentGatewayException::class, maxRetries = 3) { paymentExecutionPort.execute(request.orderId, items) }
            .recover(InventoryException::class) { cachedResult(request.orderId) }
            .getOrThrow()

        OrderCompletedEvent(
            customerId = request.customerId,
            orderId = request.orderId,
            itemCount = items.size,
        )
    }

    private fun cachedResult(orderId: OrderId): Result<PaymentExecutionException, Unit> {
        // 이전 결제 결과가 캐시에 있다고 가정
        return Unit.asSuccess()
    }
}

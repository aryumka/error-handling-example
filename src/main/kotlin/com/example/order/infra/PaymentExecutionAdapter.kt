package com.example.order.infra

import com.example.core.Result
import com.example.core.asFailure
import com.example.core.asSuccess
import com.example.order.domain.*
import com.example.order.port.PaymentExecutionPort
import org.springframework.stereotype.Component

/**
 * 결제 모듈 호출 어댑터.
 *
 * 외부 결제 모듈의 응답/예외를 인프라 예외로 변환한다.
 * 원본 예외의 종류에 따라 적절한 PaymentExecutionException 하위 타입으로 매핑한다.
 */
@Component
class PaymentExecutionAdapter(
    private val paymentClient: PaymentClient,
) : PaymentExecutionPort {

    override fun execute(orderId: OrderId, items: List<OrderItem>): Result<PaymentExecutionException, Unit> {
        return try {
            paymentClient.requestPayment(orderId.value, items.map { it.productName })
            Unit.asSuccess()
        } catch (e: Exception) {
            PaymentExceptionClassifier.classify(e).asFailure()
        }
    }
}

/**
 * 외부 결제 모듈 클라이언트 인터페이스.
 * 실제 구현은 HTTP 호출 등으로 결제 모듈과 통신한다.
 */
interface PaymentClient {
    /** null 반환 = 고객 정보 없음 */
    fun getCustomerResponse(customerId: Long): CustomerResponse?
    fun requestPayment(orderId: Long, productNames: List<String>)
}

data class CustomerResponse(
    val name: String,
    val isActive: Boolean,
)

/** PG사 API 호출 중 발생하는 예외 (결제 모듈이 던지는 원본 예외) */
class PgApiException(message: String) : RuntimeException(message)

/** 재고 시스템 자체 인프라 장애 (결제 모듈이 던지는 원본 예외) */
class InventorySystemException(message: String) : RuntimeException(message)

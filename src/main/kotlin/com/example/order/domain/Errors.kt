package com.example.order.domain

import com.example.core.BusinessError
import com.example.core.InfraException

// === Business Errors: 주문 도메인 규칙 위반 ===

data class CustomerNotFoundError(
    override val code: String = "CUSTOMER_NOT_FOUND",
    override val msg: String = "고객 정보를 찾을 수 없습니다.",
) : BusinessError

data class OrderNotReadyError(
    val orderStatus: String,
    override val code: String = "ORDER_NOT_READY",
    override val msg: String = "주문 가능한 상태가 아닙니다: $orderStatus",
) : BusinessError

data class FeatureDisabledError(
    val feature: String,
    override val code: String = "FEATURE_DISABLED",
    override val msg: String = "${feature}이(가) 비활성화되어 있습니다.",
) : BusinessError

data class EmptyCartError(
    override val code: String = "EMPTY_CART",
    override val msg: String = "장바구니가 비어 있습니다.",
) : BusinessError

// === Infra Exceptions: 결제/외부 시스템 오류 ===

/**
 * 결제 실행 오류의 공통 상위 타입.
 *
 * 결제 실행은 세 가지 원인으로 실패할 수 있다:
 *   - PG사 통신 오류 (외부 시스템)
 *   - 재고 시스템 오류 (인프라)
 *   - 결제 로직 오류 (검증/매핑 실패 등)
 */
open class PaymentExecutionException(
    message: String,
    override val code: String = "PAYMENT_EXECUTION_ERROR",
    retryable: Boolean = false,
    retryAfterSeconds: Long? = null,
) : InfraException(message, module = "payment", retryable = retryable, retryAfterSeconds = retryAfterSeconds)

class PaymentGatewayException(message: String = "PG사 통신 중 오류가 발생했습니다.") :
    PaymentExecutionException(message, code = "PAYMENT_GATEWAY_ERROR", retryable = true, retryAfterSeconds = 30)

class InventoryException(message: String = "재고 시스템 오류가 발생했습니다.") :
    PaymentExecutionException(message, code = "INVENTORY_ERROR")

class PaymentLogicException(message: String = "결제 데이터 처리 중 오류가 발생했습니다.") :
    PaymentExecutionException(message, code = "PAYMENT_LOGIC_ERROR")

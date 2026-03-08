package com.example.order.infra

import com.example.order.domain.InventoryException
import com.example.order.domain.PaymentExecutionException
import com.example.order.domain.PaymentGatewayException

/**
 * 결제 모듈이 던지는 원본 예외를 인프라 예외로 분류한다.
 *
 * 결제 모듈을 호출하는 모든 어댑터(고객 조회, 결제 실행 등)에서 동일한 분류 로직을 공유한다.
 */
object PaymentExceptionClassifier {

    fun classify(e: Exception): PaymentExecutionException = when (e) {
        is PgApiException -> PaymentGatewayException("PG사 통신 오류: ${e.message}")
        is InventorySystemException -> InventoryException("재고 시스템 오류: ${e.message}")
        else -> InventoryException("결제 모듈 알 수 없는 오류: ${e.message}")
    }
}

package com.example.order.controller

import com.example.core.BusinessError
import com.example.core.Result
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

private val log = LoggerFactory.getLogger("BusinessErrorLogger")

/**
 * Result<BusinessError, T> → ResponseEntity 변환
 *
 * - Success → 200 OK + 이벤트 데이터
 * - Failure(BusinessError) → 400 Bad Request + 에러 정보
 */
fun <T> Result<BusinessError, T>.toResponseEntity(): ResponseEntity<Any> = when (this) {
    is Result.Success -> ResponseEntity.ok(value)
    is Result.Failure -> {
        log.warn("[BUSINESS] {}: {}", error.code, error.msg)
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ErrorResponse(
                error = error.code,
                message = error.msg,
            )
        )
    }
}

data class ErrorResponse(
    val error: String,
    val module: String? = null,
    val message: String,
)

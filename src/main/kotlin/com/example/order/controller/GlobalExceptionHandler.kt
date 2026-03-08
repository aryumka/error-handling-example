package com.example.order.controller

import com.example.core.InfraException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InfraException::class)
    fun handleInfraException(ex: InfraException): ResponseEntity<ErrorResponse> {
        logWithModule(ex.module, ex)

        val status = if (ex.retryable) HttpStatus.SERVICE_UNAVAILABLE else HttpStatus.INTERNAL_SERVER_ERROR
        val headers = HttpHeaders()
        if (ex.retryable) {
            ex.retryAfterSeconds?.let { headers.set("Retry-After", it.toString()) }
        }

        return ResponseEntity.status(status).headers(headers).body(
            ErrorResponse(
                error = ex.code,
                module = ex.module,
                message = ex.message ?: "인프라 오류가 발생했습니다.",
            )
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(ex: Exception): ResponseEntity<ErrorResponse> {
        logWithModule("unknown", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                error = "UNKNOWN_ERROR",
                module = "unknown",
                message = "예상하지 못한 오류가 발생했습니다.",
            )
        )
    }

    private fun logWithModule(module: String, ex: Exception) {
        MDC.put("errorModule", module)
        try {
            log.error("[{}] {}", module, ex.message, ex)
        } finally {
            MDC.remove("errorModule")
        }
    }
}

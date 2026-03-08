package com.example.core

open class InfraException(
    message: String,
    override val code: String = "INFRA_ERROR",
    open val module: String = "unknown",
    open val retryable: Boolean = false,
    open val retryAfterSeconds: Long? = null,
) : RuntimeException(message), ResultError {
    override val msg: String get() = message ?: "인프라 오류"
}

package com.example.core

import kotlin.reflect.KClass

inline fun <E : ResultError, T, R> Result<E, T>.map(transform: (T) -> R): Result<E, R> =
    when (this) {
        is Result.Success -> Result.Success(transform(value))
        is Result.Failure -> this
    }

inline fun <E : ResultError, T, R> Result<E, T>.flatMap(
    transform: (T) -> Result<E, R>
): Result<E, R> =
    when (this) {
        is Result.Success -> transform(value)
        is Result.Failure -> this
    }

inline fun <E : ResultError, T> Result<E, T>.recoverWith(
    recover: (E) -> Result<E, T>
): Result<E, T> =
    when (this) {
        is Result.Success -> this
        is Result.Failure -> recover(error)
    }

fun <E : ResultError, T> Result<E, T>.getOrThrow(): T =
    when (this) {
        is Result.Success -> value
        is Result.Failure -> if (error is Throwable) throw error else throw IllegalStateException(error.msg)
    }

// NOTE: 에러 타입별 분기 데모용. 실무에서는 retry 간격, backoff, circuit breaker 등이 필요하므로 resilience4j 등으로 교체 필요.

@Suppress("UNCHECKED_CAST")
fun <E : ResultError, T, Target : E> Result<E, T>.retry(
    errorType: KClass<Target>,
    maxRetries: Int,
    block: () -> Result<E, T>,
): Result<E, T> =
    when (this) {
        is Result.Success -> this
        is Result.Failure -> if (errorType.isInstance(error)) {
            var lastResult: Result<E, T> = this
            repeat(maxRetries) {
                lastResult = block()
                if (lastResult.isSuccess) return lastResult
            }
            lastResult
        } else this
    }

@Suppress("UNCHECKED_CAST") // isInstance 체크 후 캐스팅하므로 안전
fun <E : ResultError, T, Target : E> Result<E, T>.recover(
    errorType: KClass<Target>,
    fallback: (Target) -> Result<E, T>,
): Result<E, T> =
    when (this) {
        is Result.Success -> this
        is Result.Failure -> if (errorType.isInstance(error)) fallback(error as Target) else this
    }

package com.example.core

sealed class Result<out E : ResultError, out T> {
    data class Success<out T>(val value: T) : Result<Nothing, T>()
    data class Failure<out E : ResultError>(val error: E) : Result<E, Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun errorOrNull(): E? = when (this) {
        is Success -> null
        is Failure -> error
    }
}

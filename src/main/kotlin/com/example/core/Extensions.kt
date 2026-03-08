package com.example.core

fun <T> T.asSuccess(): Result<Nothing, T> = Result.Success(this)

fun <E : ResultError> E.asFailure(): Result<E, Nothing> = Result.Failure(this)

inline fun <E : ResultError, T> Result<E, T>.onSuccess(action: (T) -> Unit): Result<E, T> {
    if (this is Result.Success) action(value)
    return this
}

inline fun <E : ResultError, T> Result<E, T>.onFailure(action: (E) -> Unit): Result<E, T> {
    if (this is Result.Failure) action(error)
    return this
}

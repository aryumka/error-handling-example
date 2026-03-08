package com.example.core

/**
 * resultOf { } 블록 내에서 ensure/ensureNotNull로 비즈니스 검증을 명령형 스타일로 작성할 수 있다.
 *
 * 사용 예:
 * ```
 * fun process(): Result<MyError, Output> = resultOf {
 *     val a = ensureNotNull(portA.query()) { NotFoundError() }
 *     ensure(a.isValid) { InvalidError() }
 *     Output(a)
 * }
 * ```
 */
class ResultBindException(@JvmField val error: ResultError) : Exception() {
    override fun fillInStackTrace(): Throwable = this
}

class ResultScope<E : ResultError> {
    fun <T> Result<ResultError, T>.bind(): T = when (this) {
        is Result.Success -> value
        is Result.Failure -> throw ResultBindException(error)
    }

    inline fun ensure(condition: Boolean, error: () -> E) {
        if (!condition) throw ResultBindException(error())
    }

    inline fun <T : Any> ensureNotNull(value: T?, error: () -> E): T {
        return value ?: throw ResultBindException(error())
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <E : ResultError, T> resultOf(block: ResultScope<E>.() -> T): Result<E, T> {
    return try {
        Result.Success(ResultScope<E>().block())
    } catch (e: ResultBindException) {
        Result.Failure(e.error as E)
    }
}

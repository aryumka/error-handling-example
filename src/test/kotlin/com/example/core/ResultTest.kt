package com.example.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ResultTest {

    private data class TestBusinessError(override val msg: String, override val code: String = "TEST") : BusinessError
    private data class TestError(override val msg: String, override val code: String = "TEST") : ResultError

    @Test
    fun `Success holds value`() {
        val result: Result<ResultError, String> = Result.Success("hello")

        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals("hello", result.getOrNull())
        assertNull(result.errorOrNull())
    }

    @Test
    fun `Failure holds error`() {
        val error = TestBusinessError("something wrong")
        val result: Result<TestBusinessError, String> = Result.Failure(error)

        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertNull(result.getOrNull())
        assertEquals(error, result.errorOrNull())
    }

    @Test
    fun `asSuccess creates Success`() {
        val result = "value".asSuccess()

        assertTrue(result.isSuccess)
        assertEquals("value", result.getOrNull())
    }

    @Test
    fun `asFailure creates Failure`() {
        val error = TestError("some fail")
        val result = error.asFailure()

        assertTrue(result.isFailure)
        assertEquals(error, result.errorOrNull())
    }

    @Test
    fun `onSuccess executes action on Success`() {
        var captured = ""
        "hello".asSuccess().onSuccess { captured = it }
        assertEquals("hello", captured)
    }

    @Test
    fun `onSuccess does not execute action on Failure`() {
        var executed = false
        TestBusinessError("err").asFailure().onSuccess { executed = true }
        assertFalse(executed)
    }

    @Test
    fun `onFailure executes action on Failure`() {
        var captured = ""
        TestBusinessError("err").asFailure().onFailure { captured = it.msg }
        assertEquals("err", captured)
    }

    @Test
    fun `onFailure does not execute action on Success`() {
        var executed = false
        "ok".asSuccess().onFailure { executed = true }
        assertFalse(executed)
    }
}

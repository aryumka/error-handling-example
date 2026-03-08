package com.example.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class RailwayTest {

    private data class TestBusinessError(override val msg: String, override val code: String = "TEST") : BusinessError

    @Test
    fun `map transforms Success value`() {
        val result = "hello".asSuccess().map { it.length }
        assertEquals(5, result.getOrNull())
    }

    @Test
    fun `map passes through Failure`() {
        val error = TestBusinessError("fail")
        val result = error.asFailure().map { "never" }
        assertEquals(error, result.errorOrNull())
    }

    @Test
    fun `flatMap chains Success`() {
        val result = "10".asSuccess()
            .flatMap { it.toIntOrNull()?.asSuccess() ?: TestBusinessError("parse error").asFailure() }
        assertEquals(10, result.getOrNull())
    }

    @Test
    fun `flatMap short-circuits on Failure`() {
        val error = TestBusinessError("first fail")
        var secondCalled = false
        val result = error.asFailure().flatMap {
            secondCalled = true
            "ok".asSuccess()
        }
        assertTrue(result.isFailure)
        assertEquals(false, secondCalled)
    }

    @Test
    fun `recoverWith recovers from Failure`() {
        val result = TestBusinessError("not found").asFailure()
            .recoverWith { "recovered".asSuccess() }
        assertEquals("recovered", result.getOrNull())
    }

    @Test
    fun `recoverWith passes through Success`() {
        val result = "original".asSuccess()
            .recoverWith { "recovered".asSuccess() }
        assertEquals("original", result.getOrNull())
    }

    @Test
    fun `getOrThrow returns value on Success`() {
        assertEquals("ok", "ok".asSuccess().getOrThrow())
    }

    @Test
    fun `getOrThrow throws on Failure`() {
        val ex = assertFailsWith<IllegalStateException> {
            TestBusinessError("err").asFailure().getOrThrow()
        }
        assertTrue(ex.message!!.contains("err"))
    }
}

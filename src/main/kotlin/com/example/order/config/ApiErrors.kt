package com.example.order.config

import kotlin.reflect.KClass

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ApiErrors(
    val value: Array<KClass<*>> = [],
)

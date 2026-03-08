package com.example.order.config

import com.example.core.BusinessError
import com.example.core.InfraException
import com.example.order.controller.ErrorResponse
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import org.springdoc.core.customizers.GlobalOpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * 모든 엔드포인트에 공통 에러 스키마와 UNKNOWN 500 응답을 등록한다.
 *
 * 1. ErrorResponse 스키마를 components/schemas에 등록
 * 2. 모든 엔드포인트의 500 응답에 UNKNOWN example 추가
 *    - @ApiErrors로 INFRA 500이 이미 있으면 → UNKNOWN example 병합
 *    - 500이 없으면 → UNKNOWN만으로 500 응답 생성
 */
@Component
class GlobalErrorSchemaCustomizer : GlobalOpenApiCustomizer {
    override fun customise(openApi: OpenAPI) {
        // ErrorResponse 스키마 등록
        val schemas = openApi.components?.schemas ?: mutableMapOf<String, Schema<*>>().also {
            openApi.components.schemas = it
        }
        if (!schemas.containsKey("ErrorResponse")) {
            schemas["ErrorResponse"] = Schema<ErrorResponse>().apply {
                type = "object"
                addProperty("error", StringSchema().apply { description = "에러 코드" })
                addProperty("module", StringSchema().apply { description = "담당 모듈 (INFRA 에러만 포함)"; nullable = true })
                addProperty("message", StringSchema().apply { description = "에러 메시지" })
                required = listOf("error", "message")
            }
        }

        val schemaRef = Schema<ErrorResponse>().`$ref`("#/components/schemas/ErrorResponse")
        val unknownExample = mapOf(
            "UnexpectedException" to Example().apply {
                value = ErrorResponse(
                    error = "UNKNOWN_ERROR",
                    module = "unknown",
                    message = "예상하지 못한 오류가 발생했습니다.",
                )
            }
        )

        // 모든 엔드포인트 순회하며 UNKNOWN 500 example 추가
        openApi.paths?.values?.forEach { pathItem ->
            pathItem.readOperations().forEach { operation ->
                val responses = operation.responses ?: return@forEach
                val existing500 = responses["500"]

                if (existing500 != null) {
                    // @ApiErrors에서 INFRA 500이 이미 등록된 경우 → UNKNOWN 병합
                    val mediaType = existing500.content?.get("application/json") ?: return@forEach
                    val examples = mediaType.examples ?: mutableMapOf()
                    examples.putAll(unknownExample)
                    mediaType.examples = examples
                    existing500.description = "Server Error"
                } else {
                    // 500이 없는 엔드포인트 → UNKNOWN만으로 500 응답 생성
                    responses.addApiResponse("500", ApiResponse().apply {
                        description = "Server Error"
                        content = Content().addMediaType("application/json", MediaType().apply {
                            schema = schemaRef
                            examples = unknownExample.toMutableMap()
                        })
                    })
                }
            }
        }
    }
}

@Component
class ApiErrorOperationCustomizer : OperationCustomizer {

    override fun customize(operation: Operation, handlerMethod: HandlerMethod): Operation {
        val annotation = handlerMethod.getMethodAnnotation(ApiErrors::class.java)
            ?: return operation

        val businessErrors = mutableMapOf<String, Example>()
        val infraErrors500 = mutableMapOf<String, Example>()
        val infraErrors503 = mutableMapOf<String, Example>()

        for (kClass in annotation.value) {
            val name = kClass.simpleName ?: continue

            when {
                BusinessError::class.java.isAssignableFrom(kClass.java) -> {
                    val instance = tryInstantiate(kClass)
                    val business = instance as? BusinessError
                    val code = business?.code ?: name
                    val msg = business?.msg ?: "Business error"
                    businessErrors[name] = Example().apply {
                        value = ErrorResponse(
                            error = code,
                            message = msg,
                        )
                    }
                }
                InfraException::class.java.isAssignableFrom(kClass.java) -> {
                    val instance = tryInstantiate(kClass)
                    val infra = instance as? InfraException
                    val code = infra?.code ?: name
                    val msg = infra?.message ?: "Infrastructure error"
                    val module = infra?.module ?: "unknown"
                    val example = Example().apply {
                        value = ErrorResponse(
                            error = code,
                            module = module,
                            message = msg,
                        )
                    }
                    if (infra?.retryable == true) infraErrors503[name] = example
                    else infraErrors500[name] = example
                }
            }
        }

        val schemaRef = Schema<ErrorResponse>().`$ref`("#/components/schemas/ErrorResponse")

        if (businessErrors.isNotEmpty()) {
            operation.responses.addApiResponse("400", ApiResponse().apply {
                description = "Business Error"
                content = Content().addMediaType("application/json", MediaType().apply {
                    schema = schemaRef
                    examples = businessErrors
                })
            })
        }

        if (infraErrors500.isNotEmpty()) {
            operation.responses.addApiResponse("500", ApiResponse().apply {
                description = "Server Error"
                content = Content().addMediaType("application/json", MediaType().apply {
                    schema = schemaRef
                    examples = infraErrors500
                })
            })
        }

        if (infraErrors503.isNotEmpty()) {
            operation.responses.addApiResponse("503", ApiResponse().apply {
                description = "Service Unavailable (Retry-After 헤더 포함)"
                content = Content().addMediaType("application/json", MediaType().apply {
                    schema = schemaRef
                    examples = infraErrors503
                })
            })
        }

        return operation
    }

    private fun tryInstantiate(kClass: KClass<*>): Any? {
        val ctor = kClass.primaryConstructor ?: return null
        return try {
            val args = ctor.parameters
                .filter { !it.isOptional }
                .associateWith { param ->
                    when (param.type.classifier) {
                        String::class -> "(예시)"
                        Long::class -> 0L
                        Int::class -> 0
                        Boolean::class -> false
                        else -> return null
                    }
                }
            ctor.callBy(args)
        } catch (_: Exception) {
            null
        }
    }
}

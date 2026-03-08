package com.example.order.controller

import com.example.order.domain.*
import com.example.order.port.*
import com.example.order.service.OrderAlertPolicy
import com.example.order.service.OrderService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * placeOrder 흐름에서 에러 종류별 응답을 검증한다.
 *
 * 모든 실패를 동일한 패턴으로 통일:
 *   BusinessError(도메인 규칙 위반) → Result.Failure → toResponseEntity() → 400
 *   InfraException(인프라 장애)    → throw          → @ExceptionHandler  → 500
 *
 * 에러 분류와 알림 정책은 별개 관심사:
 *   같은 400이라도 운영팀이 인지해야 하는 에러(EmptyCart, OrderNotReady)는 알림을 보내고,
 *   정상 흐름인 에러(CustomerNotFound, FeatureDisabled)는 알림을 보내지 않는다.
 */
class PlaceOrderTest {

    private lateinit var mockMvc: MockMvc

    private val customerQueryPort = FakeCustomerQueryPort()
    private val orderStatusQueryPort = FakeOrderStatusQueryPort()
    private val orderItemQueryPort = FakeOrderItemQueryPort()
    private val paymentExecutionPort = FakePaymentExecutionPort()
    private val alertPublisher = FakeAlertPublisher()

    private val requestBody = """
        {
            "customerId": {"value": 1},
            "orderId": {"value": 100}
        }
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        setupWithAutoOrderEnabled(true)
    }

    private fun setupWithAutoOrderEnabled(enabled: Boolean) {
        val service = OrderService(
            customerQueryPort, orderStatusQueryPort,
            orderItemQueryPort, paymentExecutionPort,
            autoOrderEnabled = enabled,
        )
        val controller = OrderController(service, OrderAlertPolicy(alertPublisher))

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(GlobalExceptionHandler())
            .build()

        customerQueryPort.returnNull = false
        customerQueryPort.isActive = true
        orderStatusQueryPort.status = OrderStatus.CREATED
        orderItemQueryPort.returnEmpty = false
        paymentExecutionPort.failWith = null
        paymentExecutionPort.executedItems.clear()
        alertPublisher.sentMessages.clear()
    }

    @Test
    fun `성공 시 200 OK와 완료 이벤트를 반환한다`() {
        mockMvc.post("/api/orders/place") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isOk() }
            jsonPath("$.customerId.value") { value(1) }
            jsonPath("$.orderId.value") { value(100) }
            jsonPath("$.itemCount") { value(2) }
        }

        assertTrue(alertPublisher.sentMessages.isEmpty())
    }

    // --- BusinessError → 400, 알림 없음 (정상적인 스킵 사유) ---

    @Test
    fun `1 고객 조회 실패 - 400 반환, 알림 없음`() {
        customerQueryPort.returnNull = true

        mockMvc.post("/api/orders/place") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.module") { doesNotExist() }
            jsonPath("$.error") { value("CUSTOMER_NOT_FOUND") }
        }

        assertTrue(alertPublisher.sentMessages.isEmpty())
    }

    @Test
    fun `3 기능 비활성화 - 400 반환, 알림 없음`() {
        setupWithAutoOrderEnabled(false)

        mockMvc.post("/api/orders/place") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.module") { doesNotExist() }
            jsonPath("$.error") { value("FEATURE_DISABLED") }
        }

        assertTrue(alertPublisher.sentMessages.isEmpty())
    }

    // --- BusinessError → 400, 알림 있음 (운영팀 인지 필요) ---

    @Test
    fun `2 주문 상태 이상 - 400 반환, 알림 발송`() {
        orderStatusQueryPort.status = OrderStatus.PROCESSING

        mockMvc.post("/api/orders/place") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.module") { doesNotExist() }
            jsonPath("$.error") { value("ORDER_NOT_READY") }
        }

        assertEquals(1, alertPublisher.sentMessages.size)
        assertTrue(alertPublisher.sentMessages[0].contains("PROCESSING"))
    }

    @Test
    fun `4 장바구니 비어있음 - 400 반환, 알림 발송`() {
        orderItemQueryPort.returnEmpty = true

        mockMvc.post("/api/orders/place") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.module") { doesNotExist() }
            jsonPath("$.error") { value("EMPTY_CART") }
        }

        assertEquals(1, alertPublisher.sentMessages.size)
        assertTrue(alertPublisher.sentMessages[0].contains("장바구니"))
    }

    // --- InfraException → 500/503 (결제 실행 오류 세분화) ---

    @Test
    fun `5-a PG사 통신 오류 시 503과 Retry-After를 반환한다`() {
        paymentExecutionPort.failWith = PaymentGatewayException("PG사 응답 타임아웃")

        mockMvc.post("/api/orders/place") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isServiceUnavailable() }
            header { string("Retry-After", "30") }
            jsonPath("$.module") { value("payment") }
            jsonPath("$.error") { value("PAYMENT_GATEWAY_ERROR") }
            jsonPath("$.message") { value("PG사 응답 타임아웃") }
        }
    }

    @Test
    fun `5-b 재고 시스템 오류 시 캐시로 복구하여 200을 반환한다`() {
        paymentExecutionPort.failWith = InventoryException()

        mockMvc.post("/api/orders/place") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isOk() }
            jsonPath("$.customerId.value") { value(1) }
            jsonPath("$.orderId.value") { value(100) }
        }
    }

    @Test
    fun `5-c 결제 로직 오류 시 500을 반환한다`() {
        paymentExecutionPort.failWith = PaymentLogicException("결제 금액 계산 실패")

        mockMvc.post("/api/orders/place") {
            contentType = MediaType.APPLICATION_JSON
            content = requestBody
        }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.module") { value("payment") }
            jsonPath("$.error") { value("PAYMENT_LOGIC_ERROR") }
            jsonPath("$.message") { value("결제 금액 계산 실패") }
        }
    }
}

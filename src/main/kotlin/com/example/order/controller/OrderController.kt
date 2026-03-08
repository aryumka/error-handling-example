package com.example.order.controller

import com.example.core.onFailure
import com.example.order.config.ApiErrors
import com.example.order.domain.*
import com.example.order.service.OrderAlertPolicy
import com.example.order.service.OrderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService,
    private val orderAlertPolicy: OrderAlertPolicy,
) {

    @ApiErrors([
        CustomerNotFoundError::class,
        OrderNotReadyError::class,
        FeatureDisabledError::class,
        EmptyCartError::class,
        PaymentGatewayException::class,
        InventoryException::class,
        PaymentLogicException::class,
    ])
    @PostMapping("/place")
    fun placeOrder(@RequestBody request: PlaceOrderRequest): ResponseEntity<Any> {
        return orderService.placeOrder(request)
            .onFailure { orderAlertPolicy.alertIfNeeded(it) }
            .toResponseEntity()
    }
}

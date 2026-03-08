package com.example.order.infra

import com.example.order.domain.Customer
import com.example.order.domain.CustomerId
import com.example.order.port.CustomerQueryPort
import org.springframework.stereotype.Component

@Component
class CustomerQueryAdapter(
    private val paymentClient: PaymentClient,
) : CustomerQueryPort {

    override fun getCustomer(customerId: CustomerId): Customer? {
        val response = try {
            paymentClient.getCustomerResponse(customerId.value)
        } catch (e: Exception) {
            throw PaymentExceptionClassifier.classify(e)
        } ?: return null

        return Customer(
            customerId = customerId,
            name = response.name,
            isActive = response.isActive,
        )
    }
}

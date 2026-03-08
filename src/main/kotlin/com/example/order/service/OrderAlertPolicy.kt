package com.example.order.service

import com.example.core.BusinessError
import com.example.order.domain.EmptyCartError
import com.example.order.domain.OrderNotReadyError
import com.example.order.port.AlertPublisher
import org.springframework.stereotype.Component

/**
 * 에러 분류(Business/Infra)와 알림 정책은 별개 관심사.
 *
 * 같은 400 응답이라도 운영팀이 인지해야 하는 에러는 알림을 보낸다.
 * 어떤 에러에 알림을 보낼지는 에러 타입이 아니라 여기서 결정한다.
 */
@Component
class OrderAlertPolicy(
    private val alertPublisher: AlertPublisher,
) {

    fun alertIfNeeded(error: BusinessError) {
        when (error) {
            is EmptyCartError -> alertPublisher.send("[WARN] ${error.msg}")
            is OrderNotReadyError -> alertPublisher.send("[WARN] ${error.msg}")
            else -> { }
        }
    }
}

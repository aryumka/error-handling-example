package com.example.order.infra

import com.example.order.port.AlertPublisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * 슬랙 웹훅을 직접 호출하는 AlertPublisher 구현.
 *
 * 기존 로그 파이프라인(ERROR → 슬랙)과 분리하여,
 * BusinessError 중 운영팀 인지가 필요한 케이스만 선택적으로 알림을 보낸다.
 *
 * 로그 레벨과 알림 채널이 분리되므로:
 * - 로그는 정확한 레벨(WARN)로 남고
 * - 슬랙 알림은 별도 채널로 발송된다
 *
 * 비동기 발송:
 * - @Async("slackAlertExecutor")로 Spring 관리 스레드풀에서 실행
 * - 슬랙 장애/지연이 요청 스레드에 영향을 주지 않는다
 */
@Component
open class SlackAlertPublisher(
    @param:Value("\${slack.webhook-url}") private val webhookUrl: String,
    @param:Value("\${spring.application.name:unknown}") private val serviceName: String = "unknown",
) : AlertPublisher {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient: RestClient = RestClient.builder()
        .defaultHeader("Content-Type", "application/json")
        .build()

    @Async("slackAlertExecutor")
    override fun send(message: String) {
        try {
            val payload = buildPayload(message)
            restClient.post()
                .uri(webhookUrl)
                .body(payload)
                .retrieve()
                .toBodilessEntity()
        } catch (e: Exception) {
            log.warn("슬랙 알림 발송 실패: {}", e.message)
        }
    }

    private fun buildPayload(message: String): Map<String, Any> {
        return mapOf(
            "blocks" to listOf(
                mapOf("type" to "divider"),
                mapOf(
                    "type" to "section",
                    "text" to mapOf("type" to "mrkdwn", "text" to message),
                ),
                mapOf(
                    "type" to "context",
                    "elements" to listOf(
                        mapOf("type" to "mrkdwn", "text" to "*service:* $serviceName"),
                    ),
                ),
            ),
        )
    }
}

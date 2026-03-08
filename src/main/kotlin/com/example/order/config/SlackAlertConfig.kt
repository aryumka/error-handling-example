package com.example.order.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

@EnableAsync
@Configuration
class SlackAlertConfig(
    @param:Value("\${slack.alert.core-pool-size:1}") private val corePoolSize: Int,
    @param:Value("\${slack.alert.max-pool-size:1}") private val maxPoolSize: Int,
    @param:Value("\${slack.alert.queue-capacity:100}") private val queueCapacity: Int,
    @param:Value("\${slack.alert.await-termination-seconds:10}") private val awaitTerminationSeconds: Int,
) {

    @Bean
    fun slackAlertExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = this@SlackAlertConfig.corePoolSize
            maxPoolSize = this@SlackAlertConfig.maxPoolSize
            queueCapacity = this@SlackAlertConfig.queueCapacity
            setThreadNamePrefix("slack-alert-")
            setRejectedExecutionHandler(ThreadPoolExecutor.DiscardPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(this@SlackAlertConfig.awaitTerminationSeconds)
            initialize()
        }
    }
}

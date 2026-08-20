package com.example.ping.scheduler

import com.example.ping.client.PongClient
import com.example.ping.client.PongResponse
import com.example.ping.ratelimit.FileLockRateLimiter
import org.springframework.http.HttpStatus
import reactor.core.publisher.Mono
import spock.lang.Specification
import reactor.test.StepVerifier

class PingSchedulerSpec extends Specification {

    def rateLimiter = Mock(FileLockRateLimiter)
    def pongClient = Mock(PongClient)
    def scheduler = new PingScheduler(rateLimiter, pongClient, 1000L)

    def "ping returns empty when rate limited"() {
        given:
        rateLimiter.tryAcquireAsync() >> Mono.just(false)

        when:
        def mono = scheduler.ping()

        then:
        StepVerifier.create(mono).verifyComplete()
        0 * pongClient.send()
    }

    def "ping sends when allowed and returns response"() {
        given:
        def response = new PongResponse(HttpStatus.OK, "World", null)
        rateLimiter.tryAcquireAsync() >> Mono.just(true)
        pongClient.send() >> Mono.just(response)

        when:
        def mono = scheduler.ping()

        then:
        StepVerifier.create(mono)
                .expectNext(response)
                .verifyComplete()
    }

    def "logResult handles null response"() {
        when:
        scheduler.logResult(null)

        then:
        noExceptionThrown()
    }

    def "logResult handles error response"() {
        given:
        PongResponse response = new PongResponse(null, null, "connection refused")

        when:
        scheduler.logResult(response)

        then:
        noExceptionThrown()
    }

    def "logResult handles success response"() {
        given:
        PongResponse response = new PongResponse(HttpStatus.OK, "World", null)

        when:
        scheduler.logResult(response)

        then:
        noExceptionThrown()
    }

    def "logResult handles throttled response"() {
        given:
        PongResponse response = new PongResponse(HttpStatus.TOO_MANY_REQUESTS, null, null)

        when:
        scheduler.logResult(response)

        then:
        noExceptionThrown()
    }

    def "logResult handles unexpected status"() {
        given:
        PongResponse response = new PongResponse(HttpStatus.INTERNAL_SERVER_ERROR, null, null)

        when:
        scheduler.logResult(response)

        then:
        noExceptionThrown()
    }
    def "run subscribes and stop disposes"() {
        given:
        PongResponse response = new PongResponse(HttpStatus.OK, "World", null)
        rateLimiter.tryAcquire() >> true
        pongClient.send() >> Mono.just(response)

        when:
        scheduler.run()   // 启动无限流
        Thread.sleep(1500) // 等待至少一个 tick 被处理

        then:
        noExceptionThrown()

        when:
        scheduler.stop()

        then:
        noExceptionThrown()
    }
}
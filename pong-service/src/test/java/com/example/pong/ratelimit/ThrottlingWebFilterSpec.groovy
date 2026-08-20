package com.example.pong.ratelimit

import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import spock.lang.Specification

class ThrottlingWebFilterSpec extends Specification {

    def limiter = Mock(OnePerSecondLimiter)
    def chain = Mock(WebFilterChain)
    def filter = new ThrottlingWebFilter(limiter)

    def "forwards request when allowed"() {
        given:
        def exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/hello").build())

        when:
        filter.filter(exchange, chain).block()

        then:
        1 * limiter.tryAcquire() >> true
        1 * chain.filter(exchange) >> Mono.empty()
        exchange.response.statusCode == null
    }

    def "returns 429 when throttled"() {
        given:
        def exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/hello").build())

        when:
        filter.filter(exchange, chain).block()

        then:
        1 * limiter.tryAcquire() >> false
        0 * chain.filter(_)
        exchange.response.statusCode.value() == 429
    }
}
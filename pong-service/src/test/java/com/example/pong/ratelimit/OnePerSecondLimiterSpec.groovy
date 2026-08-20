package com.example.pong.ratelimit

import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong
import java.util.function.LongSupplier

class OnePerSecondLimiterSpec extends Specification {

    def "allows only one request per second"() {
        given:
        def currentSecond = 1_000L
        def limiter = new OnePerSecondLimiter({ currentSecond } as LongSupplier)

        expect:
        limiter.tryAcquire()
        !limiter.tryAcquire()
    }

    def "resets when the second changes"() {
        given:
        def currentSecond = new AtomicLong(1_000L)
        def limiter = new OnePerSecondLimiter({ currentSecond.get() } as LongSupplier)

        expect:
        limiter.tryAcquire()
        !limiter.tryAcquire()

        when:
        currentSecond.set(1_001L)

        then:
        limiter.tryAcquire()
    }
}